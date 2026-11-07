package com.webrtcmeetpro.domain.meeting

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Duration
import java.time.LocalDateTime

/**
 * RECORDING(받아쓰는 중) → PAUSED(잠깐 끔) ↔ RECORDING → 회의가 끝나면 PROCESSING(요약 중) → READY / FAILED / EMPTY(받아쓴 말 없음).
 * 끝난 뒤에 회의가 다시 열려서 또 켜면 RECORDING 으로 돌아가고, 다시 끝나면 전체를 새로 요약한다.
 */
enum class NoteStatus { RECORDING, PAUSED, PROCESSING, READY, FAILED, EMPTY }

/**
 * 회의 하나의 AI 회의록. 켜 둔 동안 참여자 브라우저가 각자 자기 말을 받아써서 보내고([MeetingTranscript]),
 * 회의가 끝나면 서버가 Claude 로 요약·결정 사항·할 일을 만든다.
 */
@Entity
@Table(name = "meeting_notes")
class MeetingNote(
    @Column(nullable = false)
    val meetingId: Long,

    @Column(nullable = false)
    val startedBy: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "note_id")
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: NoteStatus = NoteStatus.PAUSED
        protected set

    /** 지금 켜져 있으면 켠 시각 */
    var activeSince: LocalDateTime? = null
        protected set

    /** 지금까지 켜져 있던 시간 합계 (지금 켜진 구간은 빼고) */
    @Column(nullable = false)
    var activeSeconds: Long = 0
        protected set

    var title: String? = null
        protected set

    /** 요약 결과 JSON ({"summary":[...],"decisions":[...],"actionItems":[...]}) */
    @Lob
    @Column(name = "summary")
    var resultJson: String? = null
        protected set

    var language: String? = null
        protected set

    var model: String? = null
        protected set

    var errorMessage: String? = null
        protected set

    @Column(nullable = false)
    var attempts: Int = 0
        protected set

    var generatedAt: LocalDateTime? = null
        protected set

    val isOn: Boolean get() = status == NoteStatus.RECORDING

    /** 켜진 시간 (지금 켜진 구간 포함) */
    fun totalSeconds(now: LocalDateTime = LocalDateTime.now()): Long =
        activeSeconds + (activeSince?.let { Duration.between(it, now).seconds.coerceAtLeast(0) } ?: 0)

    /** 켜기. @return 이번에 새로 켜졌으면 true */
    fun turnOn(now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (isOn) return false
        status = NoteStatus.RECORDING
        activeSince = now
        return true
    }

    /**
     * 끄기 (잠깐 끔). @return 이번 구간에 켜져 있던 초 (사용량에 더한다). 이미 꺼져 있으면 0
     */
    fun turnOff(now: LocalDateTime = LocalDateTime.now()): Long {
        val since = activeSince ?: return 0
        val seconds = Duration.between(since, now).seconds.coerceAtLeast(0)
        activeSeconds += seconds
        activeSince = null
        if (status == NoteStatus.RECORDING) status = NoteStatus.PAUSED
        return seconds
    }

    /** 회의가 끝남 → 요약 대기. 받아쓴 말이 없으면 EMPTY. @return 이번 구간에 켜져 있던 초 */
    fun close(hasLines: Boolean, now: LocalDateTime = LocalDateTime.now()): Long {
        val seconds = turnOff(now)
        if (status == NoteStatus.PAUSED) {
            status = if (hasLines) NoteStatus.PROCESSING else NoteStatus.EMPTY
            attempts = 0
            errorMessage = null
        }
        return seconds
    }

    /** 다시 요약하기 (READY·FAILED 에서) */
    fun requeue() {
        check(status == NoteStatus.READY || status == NoteStatus.FAILED) { "끝난 회의록만 다시 만들 수 있습니다." }
        status = NoteStatus.PROCESSING
        attempts = 0
        errorMessage = null
    }

    fun attempt() {
        attempts++
    }

    fun ready(title: String?, resultJson: String, language: String?, model: String, at: LocalDateTime = LocalDateTime.now()) {
        if (status != NoteStatus.PROCESSING) return
        this.title = title?.take(200)
        this.resultJson = resultJson
        this.language = language?.take(10)
        this.model = model.take(60)
        generatedAt = at
        errorMessage = null
        status = NoteStatus.READY
    }

    /** @param giveUp false 면 PROCESSING 으로 남겨 스케줄러가 다시 시도 */
    fun failed(message: String, giveUp: Boolean) {
        if (status != NoteStatus.PROCESSING) return
        errorMessage = message.take(500)
        if (giveUp) status = NoteStatus.FAILED
    }

    companion object {
        /** 요약 시도 횟수 (넘으면 FAILED) */
        const val MAX_ATTEMPTS = 3
    }
}

interface MeetingNoteRepository {
    fun save(note: MeetingNote): MeetingNote
    fun findNoteById(id: Long): MeetingNote?
    fun findByMeetingId(meetingId: Long): MeetingNote?
    fun findAllByMeetingIdIn(meetingIds: Collection<Long>): List<MeetingNote>
    fun findAllByStatus(status: NoteStatus): List<MeetingNote>
}

/** 받아쓴 말 한 줄. 말한 사람 브라우저가 자기 마이크를 받아써서 보낸다 (그래서 누가 말했는지 정확하다) */
@Entity
@Table(name = "meeting_transcripts")
class MeetingTranscript(
    @Column(nullable = false)
    val meetingId: Long,

    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val speakerName: String,

    val lang: String?,

    @Column(nullable = false)
    val content: String,

    @Column(nullable = false)
    val spokenAt: LocalDateTime = LocalDateTime.now(),
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transcript_id")
    var id: Long? = null
        protected set
}

interface MeetingTranscriptRepository {
    fun save(line: MeetingTranscript): MeetingTranscript
    fun findAllByMeetingIdOrderBySpokenAtAscIdAsc(meetingId: Long): List<MeetingTranscript>
    fun existsByMeetingId(meetingId: Long): Boolean
}

// ---------------------------------------------------------------------
// AI (external/anthropic 에 구현). 테스트에서는 가짜로 바꿔 끼운다
// ---------------------------------------------------------------------

data class NotesSpeaker(val userId: Long, val name: String)

data class NotesLine(val at: LocalDateTime, val speaker: String, val text: String)

data class NotesInput(
    val meetingTitle: String,
    val meetingDate: LocalDateTime,
    val participants: List<NotesSpeaker>,
    val lines: List<NotesLine>,
)

data class NotesActionItem(val task: String, val owner: String?, val due: String?)

data class NotesResult(
    val title: String?,
    /** 회의록을 쓴 언어 (ko, en, ja, zh ...) */
    val language: String?,
    val summary: List<String>,
    val decisions: List<String>,
    val actionItems: List<NotesActionItem>,
    val model: String,
)

/** AI 가 거절했거나(정책) 응답이 잘렸을 때. 다시 시도해도 같으므로 바로 FAILED */
class NotesRejectedException(message: String) : RuntimeException(message)

interface MeetingNotesAi {
    /** API 키가 있어서 쓸 수 있는지 */
    val enabled: Boolean

    fun summarize(input: NotesInput): NotesResult
}
