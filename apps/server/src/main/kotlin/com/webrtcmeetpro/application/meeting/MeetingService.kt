package com.webrtcmeetpro.application.meeting

import com.webrtcmeetpro.domain.chat.dto.res.ChatEventResDto
import com.webrtcmeetpro.domain.meeting.dto.res.JoinMeetingResDto
import com.webrtcmeetpro.domain.meeting.dto.res.MeetingResDto
import com.webrtcmeetpro.domain.meeting.dto.res.MeetingUserResDto
import com.webrtcmeetpro.domain.meeting.MeetingRecordingRepository
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
import com.webrtcmeetpro.domain.meeting.MeetingQueryRepository
import com.webrtcmeetpro.domain.meeting.MeetingRepository
import com.webrtcmeetpro.domain.meeting.MeetingStatus
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.team.TeamPermission
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.domain.user.UserRepository
import com.webrtcmeetpro.external.media.MediaTokenIssuer
import com.webrtcmeetpro.config.properties.AiProperties
import com.webrtcmeetpro.domain.meeting.MeetingNoteRepository
import com.webrtcmeetpro.domain.meeting.NoteStatus
import org.springframework.context.ApplicationEventPublisher
import java.security.SecureRandom
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class ScheduleMeetingCommand(
    val title: String,
    val description: String?,
    /** null 이면 지금 바로 시작 */
    val scheduledAt: LocalDateTime?,
    val durationMinutes: Int,
    /** 비우면 팀원 전체 */
    val inviteeIds: Collection<Long>,
)

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
    private val meetingQueries: MeetingQueryRepository,
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
    private val noteRepository: MeetingNoteRepository,
    private val aiProperties: AiProperties,
    private val events: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    @Transactional
    fun schedule(teamId: Long, hostId: Long, cmd: ScheduleMeetingCommand): MeetingResDto {
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

    /** past = 종료·취소 (최근 30건) */
    fun myMeetings(userId: Long, past: Boolean): List<MeetingResDto> {
        val ids = inviteeRepository.findAllByUserId(userId).map { it.meetingId }
        val statuses = if (past) listOf(MeetingStatus.ENDED, MeetingStatus.CANCELED) else listOf(MeetingStatus.SCHEDULED, MeetingStatus.LIVE)
        val meetings = meetingRepository.findAllByIdInAndStatusIn(ids, statuses)
            .filter { teamAccess.activeMemberIds(it.teamId).contains(userId) }
        val sorted = if (past) meetings.sortedByDescending { it.scheduledAt }.take(30) else meetings.sortedBy { it.scheduledAt }
        return sorted.map { view(it, userId) }
    }

    fun detail(meetingId: Long, userId: Long): MeetingResDto {
        val meeting = findMeeting(meetingId)
        teamAccess.requireMember(meeting.teamId, userId)
        return view(meeting, userId)
    }

    /** 원래 있던 사람에게는 제목·시간·길이가 바뀌었을 때만 변경 알림을 보낸다 */
    @Transactional
    fun update(meetingId: Long, userId: Long, cmd: ScheduleMeetingCommand): MeetingResDto {
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

    /** 주최자는 자기 회의를, 팀 OWNER·ADMIN 은 팀의 다른 회의도 취소할 수 있다 */
    @Transactional
    fun cancel(meetingId: Long, userId: Long) {
        val meeting = findMeeting(meetingId)
        if (meeting.hostId == userId) teamAccess.requireMember(meeting.teamId, userId)
        else teamAccess.requirePermission(meeting.teamId, userId, TeamPermission.CANCEL_ANY_MEETING)
        if (meeting.status != MeetingStatus.SCHEDULED) throw BusinessException(ErrorCode.MEETING_NOT_JOINABLE)
        meeting.cancel()
        notifyInvitees(meeting, NotificationType.MEETING_CANCELED, except = userId)
    }

    /** 채널 상시 음성방: 진행 중인 음성 회의가 있으면 그리로, 없으면 새로 연다. 사이드바에 보이므로 알림은 보내지 않는다 */
    @Transactional
    fun joinVoice(roomId: Long, userId: Long, now: LocalDateTime = LocalDateTime.now()): MeetingResDto {
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

    /** 처음 들어오는 사람이 회의를 시작시킨다 */
    @Transactional
    fun join(meetingId: Long, userId: Long): JoinMeetingResDto {
        val meeting = meetingRepository.lockMeetingById(meetingId) ?: throw BusinessException(ErrorCode.MEETING_NOT_FOUND)
        teamAccess.requireMember(meeting.teamId, userId)
        // 채널에 나중에 들어온 사람도 회의 기록에 남게 초대 대상에 넣는다
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
        // 새로고침·재접속은 이미 자리를 차지하고 있으므로 인원 제한에서 뺀다
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
        return JoinMeetingResDto(view(meeting, userId), mediaProperties.publicUrl, token)
    }

    /** JOINED 는 participantId 를 돌려줘 LEFT 때 다시 받는다 */
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
                // 채널 음성방은 바로 끝내지 않는다: 새로고침·잠깐 끊김·화상 화면 전환 때 같은 방으로 돌아오게.
                // 아무도 없이 VOICE_IDLE 이 지나면 endAbandoned 가 끝낸다
                if (meeting.status == MeetingStatus.LIVE && !meeting.isVoice) {
                    meeting.end()
                    eventRepository.save(MeetingEvent(meeting.id!!, null, MeetingEventType.MEETING_ENDED))
                    events.publishEvent(MeetingEndedEvent(meeting.id!!))
                }
                pushVoicePresence(meeting)
            }
            else -> log.warn("알 수 없는 미디어 이벤트: {}", cmd.type)
        }
        return emptyMap()
    }

    @Transactional
    fun sendDueReminders(now: LocalDateTime = LocalDateTime.now()): Int {
        val ids = meetingQueries.findDueReminderIds(MeetingStatus.SCHEDULED, now.minusMinutes(5), now.plus(Meeting.REMINDER_BEFORE))
        if (ids.isEmpty()) return 0
        val due = meetingRepository.findAllByIdInAndStatusIn(ids, listOf(MeetingStatus.SCHEDULED))
        due.forEach {
            notifyInvitees(it, NotificationType.MEETING_REMINDER, except = null)
            it.markReminded(now)
        }
        return due.size
    }

    /** 입장 토큰만 받고 사이드카에 접속하지 않으면 LIVE 로 남으므로, 예정 종료가 지났고 아무도 없으면 끝낸다 */
    @Transactional
    fun endAbandoned(now: LocalDateTime = LocalDateTime.now()): Int {
        val stale = meetingRepository.findAllByStatus(MeetingStatus.LIVE).filter {
            // 음성방은 예정 시간이 없어서 마지막 사람이 나가고 VOICE_IDLE 이 지나면 끝낸다
            val due = if (it.isVoice) now.isAfter(lastActivity(it).plus(VOICE_IDLE)) else now.isAfter(it.scheduledAt.plusMinutes(it.durationMinutes.toLong()))
            due && participantRepository.countByMeetingIdAndLeftAtIsNull(it.id!!) == 0L
        }
        stale.forEach {
            it.end(now)
            eventRepository.save(MeetingEvent(it.id!!, null, MeetingEventType.MEETING_ENDED, "abandoned"))
            events.publishEvent(MeetingEndedEvent(it.id!!))
            pushVoicePresence(it)
        }
        return stale.size
    }

    /** 아무도 안 들어왔으면 시작 시각 */
    private fun lastActivity(meeting: Meeting): LocalDateTime =
        participantRepository.findAllByMeetingId(meeting.id!!).mapNotNull { it.leftAt }.maxOrNull()
            ?: meeting.startedAt ?: meeting.scheduledAt

    private fun ensureInvitee(meeting: Meeting, userId: Long) {
        if (inviteeRepository.findAllByMeetingId(meeting.id!!).none { it.userId == userId }) {
            inviteeRepository.save(MeetingInvitee(meeting.id!!, userId))
        }
    }

    private fun pushVoicePresence(meeting: Meeting) {
        val roomId = meeting.chatRoomId ?: return
        val members = chatMemberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId }
        realtime.push(members, RealtimePublisher.CHAT, ChatEventResDto("ROOM", meeting.teamId, roomId))
    }

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

    private fun userView(u: User) = MeetingUserResDto(u.id!!, u.nickname, u.profileImageUrl)

    fun canManage(meeting: Meeting, viewerId: Long): Boolean =
        if (meeting.hostId == viewerId) teamAccess.isMember(meeting.teamId, viewerId)
        else teamAccess.can(meeting.teamId, viewerId, TeamPermission.CANCEL_ANY_MEETING)

    fun recordingAvailable(meeting: Meeting): Boolean = ownerPlan(meeting)?.recordingEnabled ?: false

    private fun ownerPlan(meeting: Meeting) =
        planService.planOfTeam(meeting.teamId)

    private fun canCancel(meeting: Meeting, viewerId: Long): Boolean =
        meeting.status == MeetingStatus.SCHEDULED &&
            if (meeting.hostId == viewerId) teamAccess.isMember(meeting.teamId, viewerId)
            else teamAccess.can(meeting.teamId, viewerId, TeamPermission.CANCEL_ANY_MEETING)

    private fun view(meeting: Meeting, viewerId: Long): MeetingResDto {
        val inviteeIds = inviteeRepository.findAllByMeetingId(meeting.id!!).map { it.userId }
        val users = userRepository.findAllByIdIn(inviteeIds + meeting.hostId).associateBy { it.id!! }
        val note = noteRepository.findByMeetingId(meeting.id!!)
        return MeetingResDto(
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
            notesStatus = note?.status,
            aiNotesOn = note?.isOn == true,
            aiNotesAvailable = aiProperties.enabled && (ownerPlan(meeting)?.aiNotesMinutes ?: 0) > 0,
        )
    }

    companion object {
        /** DB 규칙상 최대 24시간 */
        const val VOICE_MINUTES = 1440
        /** 이 안에 다시 들어오면 같은 방으로 */
        val VOICE_IDLE: java.time.Duration = java.time.Duration.ofMinutes(1)
    }
}
