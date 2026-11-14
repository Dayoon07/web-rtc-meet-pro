package com.webrtcmeetpro.application.meeting

import com.webrtcmeetpro.domain.meeting.dto.res.CaptionEventResDto
import com.webrtcmeetpro.domain.meeting.dto.res.CaptionStateResDto
import com.webrtcmeetpro.domain.meeting.dto.res.CaptionsRoomEventResDto
import com.webrtcmeetpro.domain.meeting.dto.res.NotesLineResDto
import com.webrtcmeetpro.application.plan.AiQuotaService
import com.webrtcmeetpro.application.team.TeamAccess
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.domain.meeting.CaptionTranslator
import com.webrtcmeetpro.domain.meeting.Meeting
import com.webrtcmeetpro.domain.meeting.MeetingParticipantRepository
import com.webrtcmeetpro.domain.meeting.MeetingRepository
import com.webrtcmeetpro.domain.meeting.MeetingStatus
import com.webrtcmeetpro.domain.plan.AiFeature
import com.webrtcmeetpro.domain.user.UserRepository
import com.webrtcmeetpro.infrastructure.realtime.RealtimePublisher
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * 실시간 자막·번역 (Business 플랜). 각자 브라우저가 자기 말을 받아쓰고, 서버가 회의에 있는 사람들의 언어로 번역해 보낸다.
 * 켜짐 상태는 메모리에만 두므로 서버를 재시작하면 꺼진다.
 * AI 회의록도 켜져 있으면 같은 문장을 회의록에 넣어, 받아쓰기는 브라우저에서 한 번만 한다.
 */
@Service
@Transactional(readOnly = true)
class LiveCaptionService(
    private val meetings: MeetingRepository,
    private val participants: MeetingParticipantRepository,
    private val users: UserRepository,
    private val teamAccess: TeamAccess,
    private val quota: AiQuotaService,
    private val translator: CaptionTranslator,
    private val notesService: MeetingNotesService,
    private val realtime: RealtimePublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private class Session(val teamId: Long, val activeSince: LocalDateTime) {
        val langs = ConcurrentHashMap<Long, String>()
        /** 번역 문맥용 */
        val recent = ArrayDeque<String>()
        /** 문장 순서가 섞이지 않게 회의 하나의 번역은 차례대로 */
        @Volatile var tail: CompletableFuture<Void> = CompletableFuture.completedFuture(null)
    }

    private val sessions = ConcurrentHashMap<Long, Session>()
    private val ids = AtomicLong()
    private val executor = Executors.newFixedThreadPool(4) { r -> Thread(r, "live-captions").apply { isDaemon = true } }

    @PreDestroy
    fun shutdown() = executor.shutdown()

    fun state(meetingId: Long, userId: Long, now: LocalDateTime = LocalDateTime.now()): CaptionStateResDto {
        val meeting = findMeeting(meetingId)
        teamAccess.requireMember(meeting.teamId, userId)
        return stateOf(meeting, now)
    }

    /** lang = 켠 사람이 볼 자막 언어 */
    @Transactional
    fun start(meetingId: Long, userId: Long, lang: String?, now: LocalDateTime = LocalDateTime.now()): CaptionStateResDto {
        val meeting = findMeeting(meetingId)
        requireInRoom(meeting, userId)
        if (!translator.enabled) throw BusinessException(ErrorCode.AI_NOT_CONFIGURED)
        val limit = quota.limitSeconds(meeting.teamId, AiFeature.CAPTIONS, now)
        if (limit <= 0) throw BusinessException(ErrorCode.CAPTIONS_NOT_ALLOWED)
        if (quota.usedSeconds(meeting.teamId, AiFeature.CAPTIONS, now) >= limit) {
            throw BusinessException(ErrorCode.CAPTIONS_QUOTA_EXCEEDED, mapOf("minutes" to limit / 60))
        }
        var created = false
        val session = sessions.computeIfAbsent(meetingId) { created = true; Session(meeting.teamId, now) }
        normalize(lang)?.let { session.langs[userId] = it }
        if (created) broadcast(meetingId, CaptionsRoomEventResDto(meetingId, on = true, by = users.findUserById(userId)?.nickname))
        return stateOf(meeting, now)
    }

    fun watch(meetingId: Long, userId: Long, lang: String?) {
        val session = sessions[meetingId] ?: return
        val meeting = findMeeting(meetingId)
        teamAccess.requireMember(meeting.teamId, userId)
        normalize(lang)?.let { session.langs[userId] = it }
    }

    @Transactional
    fun stop(meetingId: Long, userId: Long, now: LocalDateTime = LocalDateTime.now()): CaptionStateResDto {
        val meeting = findMeeting(meetingId)
        requireInRoom(meeting, userId)
        close(meetingId, now, by = users.findUserById(userId)?.nickname)
        return stateOf(meeting, now)
    }

    /** @return 자막이 켜져 있는지. 꺼져 있어도 AI 회의록이 켜져 있으면 회의록에는 넣는다 */
    @Transactional
    fun addLine(meetingId: Long, userId: Long, rawText: String, rawLang: String?, now: LocalDateTime = LocalDateTime.now()): NotesLineResDto {
        val meeting = findMeeting(meetingId)
        val me = participants.findAllByMeetingIdAndLeftAtIsNull(meetingId).firstOrNull { it.userId == userId }
            ?: throw BusinessException(ErrorCode.NOT_IN_MEETING)
        runCatching { notesService.addLine(meetingId, userId, rawText, rawLang, now) }
        val session = sessions[meetingId] ?: return NotesLineResDto(on = false)

        val limit = quota.limitSeconds(meeting.teamId, AiFeature.CAPTIONS, now)
        val running = Duration.between(session.activeSince, now).seconds
        if (quota.usedSeconds(meeting.teamId, AiFeature.CAPTIONS, now) + running >= limit) {
            close(meetingId, now, reason = "QUOTA")
            return NotesLineResDto(on = false)
        }
        val text = rawText.replace(Regex("\\s+"), " ").trim().take(MeetingNotesService.MAX_LINE)
        if (text.isEmpty()) return NotesLineResDto(on = true)
        val lang = normalize(rawLang) ?: "ko"
        session.langs.putIfAbsent(userId, lang)
        val speaker = me.displayName.ifBlank { "?" }
        val id = ids.incrementAndGet()
        val inRoom = participants.findAllByMeetingIdAndLeftAtIsNull(meetingId).map { it.userId }.distinct()
        // 같은 언어인 사람은 번역을 기다리지 않게 원문부터
        realtime.push(inRoom, RealtimePublisher.MEETING, CaptionEventResDto(id, meetingId, userId, speaker, text, lang, emptyMap(), pending = true))
        val context = synchronized(session.recent) {
            session.recent.toList().also {
                session.recent.addLast("$speaker: $text")
                while (session.recent.size > CONTEXT_LINES) session.recent.removeFirst()
            }
        }
        val targets = session.langs.values.toSet() - lang
        session.tail = session.tail.thenRunAsync({
            val translations = if (targets.isEmpty()) emptyMap() else try {
                translator.translate(text, lang, targets, context)
            } catch (e: Exception) {
                log.warn("자막 번역 실패 (회의 {}): {}", meetingId, e.toString())
                emptyMap()
            }
            val viewers = participants.findAllByMeetingIdAndLeftAtIsNull(meetingId).map { it.userId }.distinct()
            realtime.push(viewers, RealtimePublisher.MEETING, CaptionEventResDto(id, meetingId, userId, speaker, text, lang, translations, pending = false))
        }, executor)
        return NotesLineResDto(on = true)
    }

    @EventListener
    @Transactional
    fun onMeetingEnded(event: MeetingEndedEvent) {
        close(event.meetingId, LocalDateTime.now())
    }

    /** 켜져 있던 시간만큼 이번 달 사용량에 더한다 */
    private fun close(meetingId: Long, now: LocalDateTime, by: String? = null, reason: String? = null) {
        val session = sessions.remove(meetingId) ?: return
        quota.add(session.teamId, AiFeature.CAPTIONS, Duration.between(session.activeSince, now).seconds, now)
        broadcast(meetingId, CaptionsRoomEventResDto(meetingId, on = false, by = by, reason = reason))
    }

    private fun stateOf(meeting: Meeting, now: LocalDateTime): CaptionStateResDto {
        val limit = quota.limitSeconds(meeting.teamId, AiFeature.CAPTIONS, now)
        val running = sessions[meeting.id!!]?.let { Duration.between(it.activeSince, now).seconds } ?: 0
        val used = quota.usedSeconds(meeting.teamId, AiFeature.CAPTIONS, now) + running
        return CaptionStateResDto(
            on = sessions.containsKey(meeting.id!!),
            available = translator.enabled && limit > 0,
            remainingMinutes = (limit - used).coerceAtLeast(0) / 60,
            limitMinutes = limit / 60,
        )
    }

    private fun requireInRoom(meeting: Meeting, userId: Long) {
        teamAccess.requireMember(meeting.teamId, userId)
        if (meeting.status != MeetingStatus.LIVE) throw BusinessException(ErrorCode.MEETING_NOT_JOINABLE)
        if (participants.findAllByMeetingIdAndLeftAtIsNull(meeting.id!!).none { it.userId == userId }) {
            throw BusinessException(ErrorCode.NOT_IN_MEETING)
        }
    }

    private fun broadcast(meetingId: Long, event: Any) {
        val inRoom = participants.findAllByMeetingIdAndLeftAtIsNull(meetingId).map { it.userId }.distinct()
        realtime.push(inRoom, RealtimePublisher.MEETING, event)
    }

    private fun findMeeting(id: Long) = meetings.findMeetingById(id) ?: throw BusinessException(ErrorCode.MEETING_NOT_FOUND)

    companion object {
        const val CONTEXT_LINES = 4

        /** "ko-KR" → "ko" */
        fun normalize(lang: String?): String? =
            lang?.trim()?.lowercase()?.take(2)?.takeIf { it.matches(Regex("[a-z]{2}")) }
    }
}
