package com.webrtcmeetpro.application.meeting

import com.webrtcmeetpro.domain.meeting.MeetingRecordingRepository
import com.webrtcmeetpro.application.chat.ChatEvent
import com.webrtcmeetpro.domain.chat.ChatRoomMemberRepository
import com.webrtcmeetpro.domain.chat.ChatRoomRepository
import com.webrtcmeetpro.infrastructure.realtime.RealtimePublisher
import com.webrtcmeetpro.application.plan.PlanService
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
import com.webrtcmeetpro.domain.team.TeamPermission
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
    /** 이 화면을 보는 사람이 취소할 수 있는지: 예약 상태이고 (주최자이거나 팀에서 CANCEL_ANY_MEETING 권한) */
    val canCancel: Boolean,
    /** 고칠 수 있는지 (취소와 같은 사람, 같은 조건) */
    val canEdit: Boolean = canCancel,
    /** 지금 녹화 중인지 (회의실에 "녹화 중" 표시) */
    val recording: Boolean = false,
    /** 이 사람이 녹화를 시작할 수 있는지: 열린 회의 + (주최자 또는 팀 관리 권한) + 팀 소유자 플랜에 녹화 포함 */
    val canRecord: Boolean = false,
    /** 녹화 기능이 플랜에 포함돼 있는지 (아니면 버튼을 잠그고 플랜 안내) */
    val recordingAvailable: Boolean = false,
    /** 주최자이거나 팀 관리자 (회의 상태와 상관없이) */
    val canManage: Boolean = false,
    /** 채널 음성방이면 그 채널 */
    val chatRoomId: Long? = null,
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
    private val recordingRepository: MeetingRecordingRepository,
    private val planService: PlanService,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMemberRepository: ChatRoomMemberRepository,
    private val realtime: RealtimePublisher,
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
        return view(meeting, hostId)
    }

    /** upcoming: 예약/진행 중, past: 종료/취소 (최근 30건) */
    fun myMeetings(userId: Long, past: Boolean): List<MeetingView> {
        val ids = inviteeRepository.findAllByUserId(userId).map { it.meetingId }
        val statuses = if (past) listOf(MeetingStatus.ENDED, MeetingStatus.CANCELED) else listOf(MeetingStatus.SCHEDULED, MeetingStatus.LIVE)
        val meetings = meetingRepository.findAllByIdInAndStatusIn(ids, statuses)
            .filter { teamAccess.activeMemberIds(it.teamId).contains(userId) }
        val sorted = if (past) meetings.sortedByDescending { it.scheduledAt }.take(30) else meetings.sortedBy { it.scheduledAt }
        return sorted.map { view(it, userId) }
    }

    fun detail(meetingId: Long, userId: Long): MeetingView {
        val meeting = findMeeting(meetingId)
        teamAccess.requireMember(meeting.teamId, userId)
        return view(meeting, userId)
    }

    /**
     * 예약 회의 고치기: 취소와 같은 사람(주최자, 팀 OWNER·ADMIN), 시작 전인 회의만.
     * 새로 초대된 사람에게는 초대 알림, 원래 있던 사람에게는 (제목·시간·길이가 바뀌었으면) 변경 알림.
     */
    @Transactional
    fun update(meetingId: Long, userId: Long, cmd: ScheduleMeetingCommand): MeetingView {
        val meeting = findMeeting(meetingId)
        if (meeting.hostId == userId) teamAccess.requireMember(meeting.teamId, userId)
        else teamAccess.requirePermission(meeting.teamId, userId, TeamPermission.CANCEL_ANY_MEETING)
        if (meeting.status != MeetingStatus.SCHEDULED) throw BusinessException(ErrorCode.MEETING_NOT_EDITABLE)
        val scheduledAt = cmd.scheduledAt ?: throw BusinessException(ErrorCode.INVALID_INPUT)
        if (scheduledAt != meeting.scheduledAt && scheduledAt.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw BusinessException(ErrorCode.MEETING_TIME_IN_PAST)
        }
        val teamMembers = teamAccess.activeMemberIds(meeting.teamId)
        val requested = cmd.inviteeIds.toSet().ifEmpty { teamMembers }
        if (!teamMembers.containsAll(requested)) throw BusinessException(ErrorCode.NOT_TEAM_MEMBER)

        val notable = meeting.update(cmd.title.trim(), cmd.description?.trim()?.ifBlank { null }, scheduledAt, cmd.durationMinutes)
        // 주최자는 늘 초대 대상
        val wanted = requested + meeting.hostId
        val current = inviteeRepository.findAllByMeetingId(meetingId)
        current.filter { it.userId !in wanted }.forEach(inviteeRepository::delete)
        val currentIds = current.map { it.userId }.toSet()
        val added = wanted - currentIds
        added.forEach { inviteeRepository.save(MeetingInvitee(meetingId, it)) }

        val teamName = teamRepository.findTeamById(meeting.teamId)?.name ?: ""
        val params = mapOf("title" to meeting.title, "teamName" to teamName, "scheduledAt" to JacksonConfig.toOffsetString(meeting.scheduledAt))
        val link = "/app/meetings/${meeting.id}"
        notifications.notify(added.filter { it != userId }, NotificationType.MEETING_SCHEDULED, params, link)
        if (notable) notifications.notify((currentIds intersect wanted).filter { it != userId }, NotificationType.MEETING_UPDATED, params, link)
        return view(meeting, userId)
    }

    /** 취소: 주최자는 자기 회의를(자원 관계), 팀 OWNER·ADMIN 은 팀의 다른 회의도(역할 권한). 둘 다 팀원이어야 한다 */
    @Transactional
    fun cancel(meetingId: Long, userId: Long) {
        val meeting = findMeeting(meetingId)
        if (meeting.hostId == userId) teamAccess.requireMember(meeting.teamId, userId)
        else teamAccess.requirePermission(meeting.teamId, userId, TeamPermission.CANCEL_ANY_MEETING)
        if (meeting.status != MeetingStatus.SCHEDULED) throw BusinessException(ErrorCode.MEETING_NOT_JOINABLE)
        meeting.cancel()
        notifyInvitees(meeting, NotificationType.MEETING_CANCELED, except = userId)
    }

    /**
     * 채널 상시 음성방 (디스코드처럼): 채널 참여자가 누르면 그 채널의 진행 중인 음성 회의로, 없으면 새로 연다.
     * 알림은 보내지 않는다 (사이드바에 누가 들어와 있는지 보인다). 돌려준 회의로 /meet/{id} 에 들어가면 된다.
     */
    @Transactional
    fun joinVoice(roomId: Long, userId: Long, now: LocalDateTime = LocalDateTime.now()): MeetingView {
        val room = chatRoomRepository.lockRoomById(roomId)?.takeIf { !it.deleted && it.isGroup }
            ?: throw BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND)
        teamAccess.requireMember(room.teamId, userId)
        chatMemberRepository.findByRoomIdAndUserId(roomId, userId)?.takeIf { it.isActive } ?: throw BusinessException(ErrorCode.NOT_ROOM_MEMBER)

        val open = meetingRepository.findAllByChatRoomIdAndStatusIn(roomId, listOf(MeetingStatus.SCHEDULED, MeetingStatus.LIVE))
        // 만들기만 하고 아무도 안 들어온 음성방은 정리
        val (stale, fresh) = open.partition { it.status == MeetingStatus.SCHEDULED && it.createdDt.isBefore(now.minus(VOICE_IDLE)) }
        stale.forEach { it.cancel(now) }
        val meeting = fresh.firstOrNull() ?: meetingRepository.save(
            Meeting(
                teamId = room.teamId,
                hostId = userId,
                roomCode = newRoomCode(),
                title = room.name.orEmpty(),
                description = null,
                scheduledAt = now,
                durationMinutes = VOICE_MINUTES,
                chatRoomId = roomId,
            ).apply { markReminded(now) },
        ).also { m ->
            chatMemberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).forEach { inviteeRepository.save(MeetingInvitee(m.id!!, it.userId)) }
        }
        ensureInvitee(meeting, userId)
        return view(meeting, userId)
    }

    /** 입장: 권한/시간 확인 후 사이드카 접속 토큰 발급. 처음 들어오는 사람이 회의를 시작시킨다 */
    @Transactional
    fun join(meetingId: Long, userId: Long): JoinMeetingView {
        val meeting = meetingRepository.lockMeetingById(meetingId) ?: throw BusinessException(ErrorCode.MEETING_NOT_FOUND)
        teamAccess.requireMember(meeting.teamId, userId)
        // 채널 음성방은 그 채널 참여자만 (채널에 나중에 들어온 사람도 회의 기록에 남게 초대 대상에 넣는다)
        meeting.chatRoomId?.let { roomId ->
            chatMemberRepository.findByRoomIdAndUserId(roomId, userId)?.takeIf { it.isActive } ?: throw BusinessException(ErrorCode.NOT_ROOM_MEMBER)
            ensureInvitee(meeting, userId)
        }
        if (meeting.reopen()) {
            eventRepository.save(MeetingEvent(meeting.id!!, userId, MeetingEventType.MEETING_STARTED, "reopened"))
        }
        if (!meeting.isOpen) throw BusinessException(ErrorCode.MEETING_NOT_JOINABLE)
        if (!meeting.canJoin(userId)) {
            throw BusinessException(ErrorCode.MEETING_NOT_STARTED_YET, mapOf("startsAt" to JacksonConfig.toOffsetString(meeting.scheduledAt)))
        }
        // 회의당 인원: 워크스페이스 플랜 기준. 이미 들어와 있는 사람(새로고침·재접속)은 막지 않는다
        val plan = ownerPlan(meeting)
        val inRoom = participantRepository.findAllByMeetingIdAndLeftAtIsNull(meeting.id!!).map { it.userId }.toSet()
        if (userId !in inRoom && plan != null && !plan.canJoinMeeting(inRoom.size.toLong())) {
            throw BusinessException(ErrorCode.MEETING_FULL, mapOf("max" to plan.maxMeetingParticipants))
        }
        if (meeting.start()) {
            eventRepository.save(MeetingEvent(meeting.id!!, userId, MeetingEventType.MEETING_STARTED))
            // 음성방은 알림 대신 사이드바에 보인다
            if (!meeting.isVoice) notifyInvitees(meeting, NotificationType.MEETING_STARTED, except = userId)
        }
        val user = userRepository.findUserById(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val token = tokenIssuer.issue(userId, meeting.id!!, meeting.roomCode, user.nickname, user.profileImageUrl, meeting.hostId == userId)
        return JoinMeetingView(view(meeting, userId), mediaProperties.publicUrl, token)
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
                pushVoicePresence(meeting)
                return mapOf("participantId" to p.id)
            }
            "LEFT" -> participantRepository.findParticipantById(cmd.participantId ?: return emptyMap())?.let { p ->
                p.leave(cmd.reason ?: LeaveReason.LEFT)
                meetingRepository.findMeetingById(p.meetingId)?.let(::pushVoicePresence)
            }
            "EVENT" -> eventRepository.save(MeetingEvent(cmd.meetingId!!, cmd.userId, cmd.eventType ?: return emptyMap()))
            "ROOM_CLOSED" -> {
                val meeting = findMeeting(cmd.meetingId!!)
                participantRepository.findAllByMeetingIdAndLeftAtIsNull(meeting.id!!).forEach { it.leave(LeaveReason.MEETING_ENDED) }
                if (meeting.status == MeetingStatus.LIVE) {
                    meeting.end()
                    eventRepository.save(MeetingEvent(meeting.id!!, null, MeetingEventType.MEETING_ENDED))
                }
                pushVoicePresence(meeting)
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
            // 음성방은 아무도 없으면 2분 뒤에 끝낸다 (예정 시간이 따로 없다)
            val due = if (it.isVoice) now.isAfter((it.startedAt ?: it.scheduledAt).plus(VOICE_IDLE)) else now.isAfter(it.scheduledAt.plusMinutes(it.durationMinutes.toLong()))
            due && participantRepository.countByMeetingIdAndLeftAtIsNull(it.id!!) == 0L
        }
        stale.forEach {
            it.end(now)
            eventRepository.save(MeetingEvent(it.id!!, null, MeetingEventType.MEETING_ENDED, "abandoned"))
            pushVoicePresence(it)
        }
        return stale.size
    }

    private fun ensureInvitee(meeting: Meeting, userId: Long) {
        if (inviteeRepository.findAllByMeetingId(meeting.id!!).none { it.userId == userId }) {
            inviteeRepository.save(MeetingInvitee(meeting.id!!, userId))
        }
    }

    /** 음성방에 누가 들어오고 나가면 채널 참여자들의 사이드바를 다시 그리게 한다 */
    private fun pushVoicePresence(meeting: Meeting) {
        val roomId = meeting.chatRoomId ?: return
        val members = chatMemberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId }
        realtime.push(members, RealtimePublisher.CHAT, ChatEvent("ROOM", meeting.teamId, roomId))
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

    /** 주최자이거나 팀에서 회의를 관리할 수 있는 사람 (OWNER·ADMIN) */
    fun canManage(meeting: Meeting, viewerId: Long): Boolean =
        if (meeting.hostId == viewerId) teamAccess.isMember(meeting.teamId, viewerId)
        else teamAccess.can(meeting.teamId, viewerId, TeamPermission.CANCEL_ANY_MEETING)

    /** 녹화는 워크스페이스(팀) 플랜 기준 */
    fun recordingAvailable(meeting: Meeting): Boolean = ownerPlan(meeting)?.recordingEnabled ?: false

    private fun ownerPlan(meeting: Meeting) =
        planService.planOfTeam(meeting.teamId)

    private fun canCancel(meeting: Meeting, viewerId: Long): Boolean =
        meeting.status == MeetingStatus.SCHEDULED &&
            if (meeting.hostId == viewerId) teamAccess.isMember(meeting.teamId, viewerId)
            else teamAccess.can(meeting.teamId, viewerId, TeamPermission.CANCEL_ANY_MEETING)

    private fun view(meeting: Meeting, viewerId: Long): MeetingView {
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
            canCancel = canCancel(meeting, viewerId),
            canEdit = canCancel(meeting, viewerId),
            recording = meeting.isOpen && recordingRepository.findAllByMeetingId(meeting.id!!).any { it.isRecording },
            canRecord = meeting.isOpen && canManage(meeting, viewerId) && recordingAvailable(meeting),
            recordingAvailable = recordingAvailable(meeting),
            canManage = canManage(meeting, viewerId),
            chatRoomId = meeting.chatRoomId,
        )
    }

    companion object {
        /** 음성방은 길이 제한 없이 (DB 규칙 최대 24시간) */
        const val VOICE_MINUTES = 1440
        /** 아무도 없는 음성방을 정리하는 시간 */
        val VOICE_IDLE: java.time.Duration = java.time.Duration.ofMinutes(2)
    }
}
