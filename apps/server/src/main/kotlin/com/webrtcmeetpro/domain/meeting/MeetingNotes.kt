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
 * RECORDING ↔ PAUSED → 회의가 끝나면 PROCESSING → READY / FAILED / EMPTY(받아쓴 말 없음).
 * 끝난 뒤 회의가 다시 열려 또 켜면 RECORDING 으로 돌아가고, 다시 끝나면 전체를 새로 요약한다.
 */
enum class NoteStatus { RECORDING, PAUSED, PROCESSING, READY, FAILED, EMPTY }

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

    var activeSince: LocalDateTime? = null
        protected set

    /** 지금 켜진 구간은 빼고 */
    @Column(nullable = false)
    var activeSeconds: Long = 0
        protected set

    var title: String? = null
        protected set

    /** {"summary":[...],"decisions":[...],"actionItems":[...]} */
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

    /** 지금 켜진 구간 포함 */
    fun totalSeconds(now: LocalDateTime = LocalDateTime.now()): Long =
        activeSeconds + (activeSince?.let { Duration.between(it, now).seconds.coerceAtLeast(0) } ?: 0)

    /** @return 이번에 새로 켜졌으면 true */
    fun turnOn(now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (isOn) return false
        status = NoteStatus.RECORDING
        activeSince = now
        return true
    }

    /** @return 사용량에 더할 이번 구간의 초. 이미 꺼져 있으면 0 */
    fun turnOff(now: LocalDateTime = LocalDateTime.now()): Long {
        val since = activeSince ?: return 0
        val seconds = Duration.between(since, now).seconds.coerceAtLeast(0)
        activeSeconds += seconds
        activeSince = null
        if (status == NoteStatus.RECORDING) status = NoteStatus.PAUSED
        return seconds
    }

    /** @return 이번 구간에 켜져 있던 초 */
    fun close(hasLines: Boolean, now: LocalDateTime = LocalDateTime.now()): Long {
        val seconds = turnOff(now)
        if (status == NoteStatus.PAUSED) {
            status = if (hasLines) NoteStatus.PROCESSING else NoteStatus.EMPTY
            attempts = 0
            errorMessage = null
        }
        return seconds
    }

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

    /** @param giveUp false 면 PROCESSING 으로 남겨 스케줄러가 다시 시도한다 */
    fun failed(message: String, giveUp: Boolean) {
        if (status != NoteStatus.PROCESSING) return
        errorMessage = message.take(500)
        if (giveUp) status = NoteStatus.FAILED
    }

    companion object {
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

/** 말한 사람 브라우저가 자기 마이크를 받아써 보내므로 누가 말했는지 정확하다 */
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

// 테스트에서는 가짜로 바꿔 끼운다

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
    val language: String?,
    val summary: List<String>,
    val decisions: List<String>,
    val actionItems: List<NotesActionItem>,
    val model: String,
)

/** 정책 거절이나 응답 잘림은 다시 시도해도 같으므로 바로 FAILED 로 한다 */
class NotesRejectedException(message: String) : RuntimeException(message)

interface MeetingNotesAi {
    val enabled: Boolean

    fun summarize(input: NotesInput): NotesResult
}
