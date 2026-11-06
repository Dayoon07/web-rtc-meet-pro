package com.webrtcmeetpro.application.meeting

import com.webrtcmeetpro.application.notification.NotificationService
import com.webrtcmeetpro.application.team.TeamAccess
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.JacksonConfig
import com.webrtcmeetpro.config.properties.MediaProperties
import com.webrtcmeetpro.domain.meeting.LeaveReason
import com.webrtcmeetpro.domain.meeting.Meeting
import com.webrtcmeetpro.domain.meeting.MeetingEvent
import com.webrtcmeetpro.domain.meeting.MeetingEventRepository
import com.webrtcmeetpro.domain.meeting.MeetingEventType
import com.webrtcmeetpro.domain.meeting.MeetingInvitee
import com.webrtcmeetpro.domain.meeting.MeetingInviteeRepository
import com.webrtcmeetpro.domain.meeting.MeetingParticipant
import com.webrtcmeetpro.domain.meeting.MeetingParticipantRepository
import com.webrtcmeetpro.domain.meeting.MeetingRepository
import com.webrtcmeetpro.domain.meeting.MeetingStatus
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.domain.user.UserRepository
import com.webrtcmeetpro.external.media.MediaTokenIssuer
import java.security.SecureRandom
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class MeetingUserView(val id: Long, val nickname: String, val profileImageUrl: String?)

data class MeetingView(
    val id: Long,
    val teamId: Long,
    val teamName: String,
    val title: String,
    val description: String?,
    val status: MeetingStatus,
    val scheduledAt: LocalDateTime,
    val durationMinutes: Int,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
    val host: MeetingUserView?,
    val invitees: List<MeetingUserView>,
    val liveParticipants: Long,
)

data class ScheduleMeetingCommand(
    val title: String,
    val description: String?,
    /** null 이면 지금 바로 시작하는 회의 */
    val scheduledAt: LocalDateTime?,
    val durationMinutes: Int,
    /** 비우면 팀원 전체 */
    val inviteeIds: Collection<Long>,
)

data class JoinMeetingView(val meeting: MeetingView, val mediaUrl: String, val token: String)

/** mediasoup 사이드카가 보내는 이벤트 */
data class MediaEventCommand(
    val type: String,
    val meetingId: Long? = null,
    val userId: Long? = null,
    val participantId: Long? = null,
    val displayName: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val eventType: MeetingEventType? = null,
    val reason: LeaveReason? = null,
)

@Service
@Transactional(readOnly = true)
class MeetingService(
    private val meetingRepository: MeetingRepository,
    private val inviteeRepository: MeetingInviteeRepository,
    private val participantRepository: MeetingParticipantRepository,
    private val eventRepository: MeetingEventRepository,
    private val teamRepository: TeamRepository,
    private val userRepository: UserRepository,
    private val teamAccess: TeamAccess,
    private val notifications: NotificationService,
    private val tokenIssuer: MediaTokenIssuer,
    private val mediaProperties: MediaProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    @Transactional
    fun schedule(teamId: Long, hostId: Long, cmd: ScheduleMeetingCommand): MeetingView {
        teamAccess.requireMember(teamId, hostId)
        val now = LocalDateTime.now()
        if (cmd.scheduledAt != null && cmd.scheduledAt.isBefore(now.minusMinutes(1))) {
            throw BusinessException(ErrorCode.MEETING_TIME_IN_PAST)
        }
        val teamMembers = teamAccess.activeMemberIds(teamId)
        val requested = cmd.inviteeIds.toSet().ifEmpty { teamMembers }
        if (!teamMembers.containsAll(requested)) throw BusinessException(ErrorCode.NOT_TEAM_MEMBER)

        val instant = cmd.scheduledAt == null
        val meeting = meetingRepository.save(
            Meeting(
                teamId = teamId,
                hostId = hostId,
                roomCode = newRoomCode(),
                title = cmd.title.trim(),
                description = cmd.description?.trim()?.ifBlank { null },
                scheduledAt = cmd.scheduledAt ?: now,
                durationMinutes = cmd.durationMinutes,
            ).apply { if (instant) markReminded(now) },
        )
        (requested + hostId).forEach { inviteeRepository.save(MeetingInvitee(meeting.id!!, it)) }

        // 바로 시작 회의는 주최자가 입장할 때 MEETING_STARTED 알림이 간다
        if (!instant) notifyInvitees(meeting, NotificationType.MEETING_SCHEDULED, except = hostId)
        return view(meeting)
    }

    /** upcoming: 예약/진행 중, past: 종료/취소 (최근 30건) */
    fun myMeetings(userId: Long, past: Boolean): List<MeetingView> {
        val ids = inviteeRepository.findAllByUserId(userId).map { it.meetingId }
        val statuses = if (past) listOf(MeetingStatus.ENDED, MeetingStatus.CANCELED) else listOf(MeetingStatus.SCHEDULED, MeetingStatus.LIVE)
        val meetings = meetingRepository.findAllByIdInAndStatusIn(ids, statuses)
            .filter { teamAccess.activeMemberIds(it.teamId).contains(userId) }
        val sorted = if (past) meetings.sortedByDescending { it.scheduledAt }.take(30) else meetings.sortedBy { it.scheduledAt }
        return sorted.map(::view)
    }

    fun detail(meetingId: Long, userId: Long): MeetingView {
        val meeting = findMeeting(meetingId)
        teamAccess.requireMember(meeting.teamId, userId)
        return view(meeting)
    }

    @Transactional
    fun cancel(meetingId: Long, userId: Long) {
        val meeting = findMeeting(meetingId)
        if (meeting.hostId != userId) throw BusinessException(ErrorCode.NOT_MEETING_HOST)
        if (meeting.status != MeetingStatus.SCHEDULED) throw BusinessException(ErrorCode.MEETING_NOT_JOINABLE)
        meeting.cancel()
        notifyInvitees(meeting, NotificationType.MEETING_CANCELED, except = userId)
    }

    /** 입장: 권한/시간 확인 후 사이드카 접속 토큰 발급. 처음 들어오는 사람이 회의를 시작시킨다 */
    @Transactional
    fun join(meetingId: Long, userId: Long): JoinMeetingView {
        val meeting = meetingRepository.lockMeetingById(meetingId) ?: throw BusinessException(ErrorCode.MEETING_NOT_FOUND)
        teamAccess.requireMember(meeting.teamId, userId)
        if (meeting.reopen()) {
            eventRepository.save(MeetingEvent(meeting.id!!, userId, MeetingEventType.MEETING_STARTED, "reopened"))
        }
        if (!meeting.isOpen) throw BusinessException(ErrorCode.MEETING_NOT_JOINABLE)
        if (!meeting.canJoin(userId)) {
            throw BusinessException(ErrorCode.MEETING_NOT_STARTED_YET, mapOf("startsAt" to JacksonConfig.toOffsetString(meeting.scheduledAt)))
        }
        if (meeting.start()) {
            eventRepository.save(MeetingEvent(meeting.id!!, userId, MeetingEventType.MEETING_STARTED))
            notifyInvitees(meeting, NotificationType.MEETING_STARTED, except = userId)
        }
        val user = userRepository.findUserById(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val token = tokenIssuer.issue(userId, meeting.id!!, meeting.roomCode, user.nickname, user.profileImageUrl, meeting.hostId == userId)
        return JoinMeetingView(view(meeting), mediaProperties.publicUrl, token)
    }

    /** 사이드카 이벤트 기록. JOINED 는 participantId 를 돌려줘서 LEFT 때 다시 받는다 */
    @Transactional
    fun recordMediaEvent(cmd: MediaEventCommand): Map<String, Any?> {
        when (cmd.type) {
            "JOINED" -> {
                val meeting = findMeeting(cmd.meetingId ?: throw BusinessException(ErrorCode.INVALID_INPUT))
                val p = participantRepository.save(
                    MeetingParticipant(meeting.id!!, cmd.userId!!, cmd.displayName ?: "", cmd.ipAddress?.take(45), cmd.userAgent?.take(500)),
                )
                return mapOf("participantId" to p.id)
            }
            "LEFT" -> participantRepository.findParticipantById(cmd.participantId ?: return emptyMap())
                ?.leave(cmd.reason ?: LeaveReason.LEFT)
            "EVENT" -> eventRepository.save(MeetingEvent(cmd.meetingId!!, cmd.userId, cmd.eventType ?: return emptyMap()))
            "ROOM_CLOSED" -> {
                val meeting = findMeeting(cmd.meetingId!!)
                participantRepository.findAllByMeetingIdAndLeftAtIsNull(meeting.id!!).forEach { it.leave(LeaveReason.MEETING_ENDED) }
                if (meeting.status == MeetingStatus.LIVE) {
                    meeting.end()
                    eventRepository.save(MeetingEvent(meeting.id!!, null, MeetingEventType.MEETING_ENDED))
                }
            }
            else -> log.warn("알 수 없는 미디어 이벤트: {}", cmd.type)
        }
        return emptyMap()
    }

    /** 시작 10분 전 알림. MeetingReminderScheduler 가 주기적으로 호출 */
    @Transactional
    fun sendDueReminders(now: LocalDateTime = LocalDateTime.now()): Int {
        val due = meetingRepository.findAllByStatusAndReminderSentAtIsNullAndScheduledAtBetween(
            MeetingStatus.SCHEDULED,
            now.minusMinutes(5),
            now.plus(Meeting.REMINDER_BEFORE),
        )
        due.forEach {
            notifyInvitees(it, NotificationType.MEETING_REMINDER, except = null)
            it.markReminded(now)
        }
        return due.size
    }

    /**
     * 입장 토큰만 받고 사이드카에 접속하지 않은 경우 등으로 LIVE 에 남은 회의 정리:
     * 예정 종료 시각이 지났고 접속 중인 사람이 없으면 ENDED.
     */
    @Transactional
    fun endAbandoned(now: LocalDateTime = LocalDateTime.now()): Int {
        val stale = meetingRepository.findAllByStatus(MeetingStatus.LIVE).filter {
            now.isAfter(it.scheduledAt.plusMinutes(it.durationMinutes.toLong())) &&
                participantRepository.countByMeetingIdAndLeftAtIsNull(it.id!!) == 0L
        }
        stale.forEach {
            it.end(now)
            eventRepository.save(MeetingEvent(it.id!!, null, MeetingEventType.MEETING_ENDED, "abandoned"))
        }
        return stale.size
    }

    // ---------------------------------------------------------------

    private fun findMeeting(id: Long) = meetingRepository.findMeetingById(id) ?: throw BusinessException(ErrorCode.MEETING_NOT_FOUND)

    private fun notifyInvitees(meeting: Meeting, type: NotificationType, except: Long?) {
        val teamName = teamRepository.findTeamById(meeting.teamId)?.name ?: ""
        val targets = inviteeRepository.findAllByMeetingId(meeting.id!!).map { it.userId }.filter { it != except }
        notifications.notify(
            targets,
            type,
            mapOf("title" to meeting.title, "teamName" to teamName, "scheduledAt" to JacksonConfig.toOffsetString(meeting.scheduledAt)),
            "/app/meetings/${meeting.id}",
        )
    }

    private fun newRoomCode(): String {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        return (1..10).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun userView(u: User) = MeetingUserView(u.id!!, u.nickname, u.profileImageUrl)

    private fun view(meeting: Meeting): MeetingView {
        val inviteeIds = inviteeRepository.findAllByMeetingId(meeting.id!!).map { it.userId }
        val users = userRepository.findAllByIdIn(inviteeIds + meeting.hostId).associateBy { it.id!! }
        return MeetingView(
            id = meeting.id!!,
            teamId = meeting.teamId,
            teamName = teamRepository.findTeamById(meeting.teamId)?.name ?: "",
            title = meeting.title,
            description = meeting.description,
            status = meeting.status,
            scheduledAt = meeting.scheduledAt,
            durationMinutes = meeting.durationMinutes,
            startedAt = meeting.startedAt,
            endedAt = meeting.endedAt,
            host = users[meeting.hostId]?.let(::userView),
            invitees = inviteeIds.mapNotNull { users[it] }.map(::userView),
            liveParticipants = if (meeting.status == MeetingStatus.LIVE) participantRepository.countByMeetingIdAndLeftAtIsNull(meeting.id!!) else 0,
        )
    }
}
