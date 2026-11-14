package com.webrtcmeetpro.application.meeting

import com.webrtcmeetpro.domain.meeting.dto.res.MeetingNotesResDto
import com.webrtcmeetpro.domain.meeting.dto.res.MeetingUserResDto
import com.webrtcmeetpro.domain.meeting.dto.res.NotesActionItemResDto
import com.webrtcmeetpro.domain.meeting.dto.res.NotesLineResDto
import com.webrtcmeetpro.domain.meeting.dto.res.NotesRoomEventResDto
import com.webrtcmeetpro.domain.meeting.dto.res.NotesStateResDto
import com.webrtcmeetpro.domain.meeting.dto.res.TranscriptLineResDto
import com.webrtcmeetpro.application.chat.ChatService
import com.webrtcmeetpro.application.notification.NotificationService
import com.webrtcmeetpro.application.plan.AiQuotaService
import com.webrtcmeetpro.application.team.TeamAccess
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.domain.meeting.Meeting
import com.webrtcmeetpro.domain.meeting.MeetingNote
import com.webrtcmeetpro.domain.meeting.MeetingNoteRepository
import com.webrtcmeetpro.domain.meeting.MeetingNotesAi
import com.webrtcmeetpro.domain.meeting.MeetingParticipantRepository
import com.webrtcmeetpro.domain.meeting.MeetingRepository
import com.webrtcmeetpro.domain.meeting.MeetingStatus
import com.webrtcmeetpro.domain.meeting.MeetingTranscript
import com.webrtcmeetpro.domain.meeting.MeetingTranscriptRepository
import com.webrtcmeetpro.domain.meeting.NoteStatus
import com.webrtcmeetpro.domain.meeting.NotesInput
import com.webrtcmeetpro.domain.meeting.NotesLine
import com.webrtcmeetpro.domain.meeting.NotesRejectedException
import com.webrtcmeetpro.domain.meeting.NotesResult
import com.webrtcmeetpro.domain.meeting.NotesSpeaker
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.plan.AiFeature
import com.webrtcmeetpro.domain.user.UserRepository
import com.webrtcmeetpro.infrastructure.realtime.RealtimePublisher
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** 회의가 끝났다 (MeetingService 가 같은 트랜잭션 안에서 알린다) */
data class MeetingEndedEvent(val meetingId: Long)

/**
 * AI 회의록.
 * 1) 회의 중 누군가 켜면 참여자 브라우저가 각자 자기 마이크를 받아써서(Web Speech) 한 문장씩 보낸다.
 * 2) 회의가 끝나면(모두 나감) 받아쓴 내용을 Claude 로 요약 → 요약·결정 사항·할 일(담당자·기한).
 * 3) 채널 음성방이면 그 채널에 카드로 올리고, 참석한 사람에게 알림. 회의 상세 화면에서 볼 수 있다.
 * 플랜: 워크스페이스 플랜의 한 달 AI 회의록 시간(plans.ai_notes_minutes) 안에서. 켜져 있던 시간만큼 쓴다.
 */
@Service
@Transactional(readOnly = true)
class MeetingNotesService(
    private val notes: MeetingNoteRepository,
    private val transcripts: MeetingTranscriptRepository,
    private val meetings: MeetingRepository,
    private val participants: MeetingParticipantRepository,
    private val users: UserRepository,
    private val meetingService: MeetingService,
    private val teamAccess: TeamAccess,
    private val quota: AiQuotaService,
    private val ai: MeetingNotesAi,
    private val chatService: ChatService,
    private val notifications: NotificationService,
    private val realtime: RealtimePublisher,
    private val json: JsonMapper,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    /** 요약 중인 회의록 (같은 것을 두 번 돌리지 않게) */
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    /** 요약은 오래 걸릴 수 있어서 따로 (2개씩) */
    private val executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "meeting-notes").apply { isDaemon = true } }

    @PreDestroy
    fun shutdown() = executor.shutdown()

    /** 회의실 화면: 지금 상태와 남은 시간 */
    fun state(meetingId: Long, userId: Long, now: LocalDateTime = LocalDateTime.now()): NotesStateResDto {
        val meeting = findMeeting(meetingId)
        teamAccess.requireMember(meeting.teamId, userId)
        return stateOf(meeting, notes.findByMeetingId(meetingId), now)
    }

    /** 켜기: 회의에 들어와 있는 사람이면 누구나. 모두의 화면에 "AI 회의록 작성 중" */
    @Transactional
    fun start(meetingId: Long, userId: Long, now: LocalDateTime = LocalDateTime.now()): NotesStateResDto {
        val meeting = meetings.lockMeetingById(meetingId) ?: throw BusinessException(ErrorCode.MEETING_NOT_FOUND)
        requireInRoom(meeting, userId)
        if (!ai.enabled) throw BusinessException(ErrorCode.AI_NOT_CONFIGURED)
        val limit = quota.limitSeconds(meeting.teamId, AiFeature.NOTES, now)
        if (limit <= 0) throw BusinessException(ErrorCode.AI_NOTES_NOT_ALLOWED)
        if (quota.usedSeconds(meeting.teamId, AiFeature.NOTES, now) >= limit) {
            throw BusinessException(ErrorCode.AI_NOTES_QUOTA_EXCEEDED, mapOf("minutes" to limit / 60))
        }
        val note = notes.findByMeetingId(meetingId) ?: notes.save(MeetingNote(meetingId, userId))
        if (note.turnOn(now)) broadcast(meetingId, on = true, by = users.findUserById(userId)?.nickname)
        return stateOf(meeting, note, now)
    }

    /** 끄기 (잠깐). 켜져 있던 시간만큼 이번 달 사용량에 더한다 */
    @Transactional
    fun stop(meetingId: Long, userId: Long, now: LocalDateTime = LocalDateTime.now()): NotesStateResDto {
        val meeting = meetings.lockMeetingById(meetingId) ?: throw BusinessException(ErrorCode.MEETING_NOT_FOUND)
        requireInRoom(meeting, userId)
        val note = notes.findByMeetingId(meetingId)
        if (note != null && note.isOn) {
            quota.add(meeting.teamId, AiFeature.NOTES, note.turnOff(now), now)
            broadcast(meetingId, on = false, by = users.findUserById(userId)?.nickname)
        }
        return stateOf(meeting, note, now)
    }

    /**
     * 받아쓴 한 문장. 켜져 있을 때만 저장하고, 꺼져 있으면 on=false 를 돌려줘서 화면이 받아쓰기를 멈춘다.
     * 이번 달 시간을 다 쓰면 여기서 끈다.
     */
    @Transactional
    fun addLine(meetingId: Long, userId: Long, rawText: String, lang: String?, now: LocalDateTime = LocalDateTime.now()): NotesLineResDto {
        val meeting = findMeeting(meetingId)
        val me = participants.findAllByMeetingIdAndLeftAtIsNull(meetingId).firstOrNull { it.userId == userId }
            ?: throw BusinessException(ErrorCode.NOT_IN_MEETING)
        val note = notes.findByMeetingId(meetingId)?.takeIf { it.isOn } ?: return NotesLineResDto(on = false)
        val text = rawText.replace(Regex("\\s+"), " ").trim().take(MAX_LINE)
        if (text.isNotEmpty()) {
            transcripts.save(MeetingTranscript(meetingId, userId, me.displayName.ifBlank { "?" }.take(50), lang?.take(10), text, now))
        }
        val limit = quota.limitSeconds(meeting.teamId, AiFeature.NOTES, now)
        val running = note.totalSeconds(now) - note.activeSeconds
        if (quota.usedSeconds(meeting.teamId, AiFeature.NOTES, now) + running >= limit) {
            quota.add(meeting.teamId, AiFeature.NOTES, note.turnOff(now), now)
            broadcast(meetingId, on = false, reason = "QUOTA")
            return NotesLineResDto(on = false)
        }
        return NotesLineResDto(on = true)
    }

    /** 회의록 (팀원이면 볼 수 있다) */
    fun view(meetingId: Long, userId: Long): MeetingNotesResDto {
        val meeting = findMeeting(meetingId)
        teamAccess.requireMember(meeting.teamId, userId)
        val note = notes.findByMeetingId(meetingId) ?: throw BusinessException(ErrorCode.AI_NOTES_NOT_FOUND)
        return view(meeting, note, userId)
    }

    /** 받아쓴 내용 전체 (팀원) */
    fun transcript(meetingId: Long, userId: Long): List<TranscriptLineResDto> {
        val meeting = findMeeting(meetingId)
        teamAccess.requireMember(meeting.teamId, userId)
        if (notes.findByMeetingId(meetingId) == null) throw BusinessException(ErrorCode.AI_NOTES_NOT_FOUND)
        return transcripts.findAllByMeetingIdOrderBySpokenAtAscIdAsc(meetingId)
            .map { TranscriptLineResDto(it.id!!, it.userId, it.speakerName, it.content, it.spokenAt) }
    }

    /** 다시 만들기: 주최자·관리자, 끝난(READY·FAILED) 회의록 */
    @Transactional
    fun regenerate(meetingId: Long, userId: Long): MeetingNotesResDto {
        val meeting = findMeeting(meetingId)
        teamAccess.requireMember(meeting.teamId, userId)
        if (!meetingService.canManage(meeting, userId)) throw BusinessException(ErrorCode.NOT_MEETING_HOST)
        if (!ai.enabled) throw BusinessException(ErrorCode.AI_NOT_CONFIGURED)
        val note = notes.findByMeetingId(meetingId) ?: throw BusinessException(ErrorCode.AI_NOTES_NOT_FOUND)
        if (note.status != NoteStatus.READY && note.status != NoteStatus.FAILED) throw BusinessException(ErrorCode.AI_NOTES_NOT_READY)
        note.requeue()
        submitAfterCommit(note.id!!)
        return view(meeting, note, userId)
    }

    /** 회의가 끝나면 받아쓰기를 닫고 요약을 시작한다 (커밋된 뒤 다른 스레드에서) */
    @EventListener
    @Transactional
    fun onMeetingEnded(event: MeetingEndedEvent) {
        val meeting = meetings.findMeetingById(event.meetingId) ?: return
        val note = notes.findByMeetingId(event.meetingId) ?: return
        close(meeting, note, LocalDateTime.now())
    }

    /**
     * 스케줄러: 1) 끝난 회의인데 아직 열려 있는 회의록 닫기 (이벤트를 놓친 경우)
     * 2) 서버 재시작·일시 오류로 PROCESSING 에 남은 회의록 다시 요약
     */
    @Transactional
    fun processPending(now: LocalDateTime = LocalDateTime.now()): Int {
        var count = 0
        (notes.findAllByStatus(NoteStatus.RECORDING) + notes.findAllByStatus(NoteStatus.PAUSED)).forEach { note ->
            val meeting = meetings.findMeetingById(note.meetingId) ?: return@forEach
            if (meeting.status == MeetingStatus.ENDED || meeting.status == MeetingStatus.CANCELED) {
                close(meeting, note, now)
                count++
            }
        }
        notes.findAllByStatus(NoteStatus.PROCESSING)
            .filter { it.id !in inFlight && it.editedDt.isBefore(now.minus(RETRY_AFTER)) }
            .forEach {
                submitAfterCommit(it.id!!)
                count++
            }
        return count
    }

    // ---------------------------------------------------------------

    private fun close(meeting: Meeting, note: MeetingNote, now: LocalDateTime) {
        val wasOn = note.isOn
        if (note.status != NoteStatus.RECORDING && note.status != NoteStatus.PAUSED) return
        quota.add(meeting.teamId, AiFeature.NOTES, note.close(transcripts.existsByMeetingId(meeting.id!!), now), now)
        if (wasOn) broadcast(meeting.id!!, on = false)
        if (note.status == NoteStatus.PROCESSING) submitAfterCommit(note.id!!)
    }

    private fun submitAfterCommit(noteId: Long) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = submit(noteId)
            })
        } else {
            submit(noteId)
        }
    }

    private fun submit(noteId: Long) {
        if (!inFlight.add(noteId)) return
        executor.execute {
            try {
                summarize(noteId)
            } finally {
                inFlight.remove(noteId)
            }
        }
    }

    /** 다른 스레드: 읽기 → Claude 호출(트랜잭션 밖) → 결과 저장·채널 게시·알림 */
    private fun summarize(noteId: Long) {
        val input = tx.execute {
            val note = findNote(noteId) ?: return@execute null
            if (note.status != NoteStatus.PROCESSING) return@execute null
            note.attempt()
            buildInput(note.meetingId)
        } ?: return

        val result = try {
            if (!ai.enabled) throw NotesRejectedException("AI_NOT_CONFIGURED")
            ai.summarize(input)
        } catch (e: Exception) {
            val rejected = e is NotesRejectedException
            log.warn("회의록 {} 요약 실패{}: {}", noteId, if (rejected) " (다시 시도 안 함)" else "", e.toString())
            tx.executeWithoutResult {
                findNote(noteId)?.let { it.failed(e.message ?: e.javaClass.simpleName, giveUp = rejected || it.attempts >= MeetingNote.MAX_ATTEMPTS) }
            }
            return
        }

        tx.executeWithoutResult { save(noteId, result) }
        log.info("회의록 {} 완료 (할 일 {}개)", noteId, result.actionItems.size)
    }

    private fun buildInput(meetingId: Long): NotesInput {
        val meeting = meetings.findMeetingById(meetingId)!!
        val lines = transcripts.findAllByMeetingIdOrderBySpokenAtAscIdAsc(meetingId)
        val speakers = speakers(meetingId)
        return NotesInput(
            meetingTitle = meeting.title,
            meetingDate = meeting.startedAt ?: meeting.scheduledAt,
            participants = speakers,
            lines = lines.map { NotesLine(it.spokenAt, it.speakerName, it.content) },
        )
    }

    /** 참석한 사람 (이름이 같으면 처음 한 명) */
    private fun speakers(meetingId: Long): List<NotesSpeaker> =
        participants.findAllByMeetingId(meetingId).distinctBy { it.userId }.map { NotesSpeaker(it.userId, it.displayName) }

    private fun save(noteId: Long, result: NotesResult) {
        val note = findNote(noteId) ?: return
        if (note.status != NoteStatus.PROCESSING) return
        val meeting = meetings.findMeetingById(note.meetingId) ?: return
        val owners = speakers(meeting.id!!).associate { it.name to it.userId }
        val stored = StoredNotes(
            summary = result.summary,
            decisions = result.decisions,
            actionItems = result.actionItems.map { StoredAction(it.task, it.owner, it.owner?.let(owners::get), it.due) },
        )
        note.ready(result.title, json.writeValueAsString(stored), result.language, result.model)

        // 채널 음성방이면 그 채널에 카드로
        meeting.chatRoomId?.let { roomId ->
            chatService.postMeetingNotes(
                roomId, meeting.id!!,
                mapOf(
                    "title" to (result.title ?: meeting.title),
                    "summary" to result.summary.take(3),
                    "decisions" to result.decisions.size,
                    "actions" to result.actionItems.size,
                ),
            )
        }
        val attended = participants.findAllByMeetingId(meeting.id!!).map { it.userId }.distinct()
        notifications.notify(
            attended,
            NotificationType.MEETING_NOTES_READY,
            mapOf("title" to (result.title ?: meeting.title), "actions" to result.actionItems.size),
            "/app/meetings/${meeting.id}",
        )
    }

    private fun findNote(noteId: Long): MeetingNote? = notes.findNoteById(noteId)

    private fun stateOf(meeting: Meeting, note: MeetingNote?, now: LocalDateTime): NotesStateResDto {
        val limit = quota.limitSeconds(meeting.teamId, AiFeature.NOTES, now)
        val used = quota.usedSeconds(meeting.teamId, AiFeature.NOTES, now) + (note?.let { it.totalSeconds(now) - it.activeSeconds } ?: 0)
        return NotesStateResDto(
            on = note?.isOn == true,
            available = ai.enabled && limit > 0,
            remainingMinutes = ((limit - used).coerceAtLeast(0)) / 60,
            limitMinutes = limit / 60,
        )
    }

    private fun view(meeting: Meeting, note: MeetingNote, viewerId: Long): MeetingNotesResDto {
        val stored: StoredNotes? = note.resultJson?.let { runCatching { json.readValue<StoredNotes>(it) }.getOrNull() }
        return MeetingNotesResDto(
            meetingId = meeting.id!!,
            status = note.status,
            on = note.isOn,
            startedBy = users.findUserById(note.startedBy)?.let { MeetingUserResDto(it.id!!, it.nickname, it.profileImageUrl) },
            title = note.title,
            summary = stored?.summary.orEmpty(),
            decisions = stored?.decisions.orEmpty(),
            actionItems = stored?.actionItems.orEmpty().map { NotesActionItemResDto(it.task, it.owner, it.ownerUserId, it.due) },
            language = note.language,
            generatedAt = note.generatedAt,
            minutes = (note.totalSeconds() + 59) / 60,
            lineCount = transcripts.findAllByMeetingIdOrderBySpokenAtAscIdAsc(meeting.id!!).size,
            canRegenerate = (note.status == NoteStatus.READY || note.status == NoteStatus.FAILED) && meetingService.canManage(meeting, viewerId),
        )
    }

    private fun requireInRoom(meeting: Meeting, userId: Long) {
        teamAccess.requireMember(meeting.teamId, userId)
        if (meeting.status != MeetingStatus.LIVE) throw BusinessException(ErrorCode.MEETING_NOT_JOINABLE)
        if (participants.findAllByMeetingIdAndLeftAtIsNull(meeting.id!!).none { it.userId == userId }) {
            throw BusinessException(ErrorCode.NOT_IN_MEETING)
        }
    }

    private fun broadcast(meetingId: Long, on: Boolean, by: String? = null, reason: String? = null) {
        val inRoom = participants.findAllByMeetingIdAndLeftAtIsNull(meetingId).map { it.userId }.distinct()
        realtime.push(inRoom, RealtimePublisher.MEETING, NotesRoomEventResDto(meetingId, on, by, reason))
    }

    private fun findMeeting(id: Long) = meetings.findMeetingById(id) ?: throw BusinessException(ErrorCode.MEETING_NOT_FOUND)

    /** meeting_notes.summary 에 넣는 JSON */
    private data class StoredAction(val task: String = "", val owner: String? = null, val ownerUserId: Long? = null, val due: String? = null)
    private data class StoredNotes(
        val summary: List<String> = emptyList(),
        val decisions: List<String> = emptyList(),
        val actionItems: List<StoredAction> = emptyList(),
    )

    companion object {
        const val MAX_LINE = 1000
        val RETRY_AFTER: Duration = Duration.ofMinutes(2)
    }
}
