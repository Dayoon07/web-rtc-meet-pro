package com.webrtcmeetpro.domain.meeting

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Duration
import java.time.LocalDateTime

enum class MeetingStatus { SCHEDULED, LIVE, ENDED, CANCELED }

/**
 * 회의. 예약(SCHEDULED) → 첫 입장 시 LIVE → 마지막 사람이 나가면 ENDED.
 * SFU 의 Router / Transport / Producer 같은 미디어 상태는 mediasoup 사이드카 메모리에만 있다.
 */
@Entity
@Table(name = "meetings")
class Meeting(
    @Column(nullable = false)
    val teamId: Long,

    @Column(nullable = false)
    val hostId: Long,

    @Column(nullable = false, unique = true)
    val roomCode: String,

    @Column(nullable = false)
    var title: String,

    var description: String?,

    /** 바로 시작하는 회의는 생성 시각 */
    var scheduledAt: LocalDateTime,

    @Column(nullable = false)
    var durationMinutes: Int = 60,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meeting_id")
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: MeetingStatus = MeetingStatus.SCHEDULED
        protected set

    var reminderSentAt: LocalDateTime? = null
        protected set

    var startedAt: LocalDateTime? = null
        protected set

    var endedAt: LocalDateTime? = null
        protected set

    var canceledAt: LocalDateTime? = null
        protected set

    val isOpen: Boolean get() = status == MeetingStatus.SCHEDULED || status == MeetingStatus.LIVE

    /** 주최자는 언제든, 다른 사람은 시작 EARLY_JOIN 전부터 입장 가능 */
    fun canJoin(userId: Long, now: LocalDateTime = LocalDateTime.now()) =
        status == MeetingStatus.LIVE || userId == hostId || !now.isBefore(scheduledAt.minus(EARLY_JOIN))

    /** @return 이번 호출로 처음 시작됐으면 true */
    fun start(at: LocalDateTime = LocalDateTime.now()): Boolean {
        if (status != MeetingStatus.SCHEDULED) return false
        status = MeetingStatus.LIVE
        startedAt = at
        return true
    }

    fun end(at: LocalDateTime = LocalDateTime.now()) {
        if (status == MeetingStatus.LIVE) {
            status = MeetingStatus.ENDED
            endedAt = at
        }
    }

    /**
     * 모두 나가서 ENDED 가 됐어도 예정 시간이 안 지났으면 다시 들어올 수 있다
     * (마지막 사람이 새로고침하거나 네트워크가 잠깐 끊긴 경우).
     */
    fun canReopen(now: LocalDateTime = LocalDateTime.now()) =
        status == MeetingStatus.ENDED && now.isBefore(scheduledAt.plusMinutes(durationMinutes.toLong()))

    /** @return 다시 열었으면 true */
    fun reopen(now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (!canReopen(now)) return false
        status = MeetingStatus.LIVE
        endedAt = null
        return true
    }

    fun cancel(at: LocalDateTime = LocalDateTime.now()) {
        check(status == MeetingStatus.SCHEDULED) { "예약 상태에서만 취소할 수 있습니다." }
        status = MeetingStatus.CANCELED
        canceledAt = at
    }

    /**
     * 예약 회의 고치기. 시간이 바뀌면 10분 전 알림을 다시 보내도록 표시를 지운다.
     * @return 사람들에게 알릴 만한 변경(제목·시간·길이)이 있었는지
     */
    fun update(title: String, description: String?, scheduledAt: LocalDateTime, durationMinutes: Int): Boolean {
        check(status == MeetingStatus.SCHEDULED) { "예약 상태에서만 고칠 수 있습니다." }
        val notable = title != this.title || scheduledAt != this.scheduledAt || durationMinutes != this.durationMinutes
        if (scheduledAt != this.scheduledAt) reminderSentAt = null
        this.title = title
        this.description = description
        this.scheduledAt = scheduledAt
        this.durationMinutes = durationMinutes
        return notable
    }

    fun markReminded(at: LocalDateTime = LocalDateTime.now()) {
        reminderSentAt = at
    }

    companion object {
        val EARLY_JOIN: Duration = Duration.ofMinutes(10)
        val REMINDER_BEFORE: Duration = Duration.ofMinutes(10)
    }
}

interface MeetingRepository {
    fun save(meeting: Meeting): Meeting
    fun findMeetingById(id: Long): Meeting?
    /** 행 잠금(SELECT ... FOR UPDATE). 여러 명이 동시에 입장해도 시작/재시작이 한 번만 일어나게 */
    fun lockMeetingById(id: Long): Meeting?
    fun findByRoomCode(roomCode: String): Meeting?
    fun findAllByIdInAndStatusIn(ids: Collection<Long>, statuses: Collection<MeetingStatus>): List<Meeting>
    fun findAllByStatus(status: MeetingStatus): List<Meeting>
    fun findAllByTeamIdAndStatus(teamId: Long, status: MeetingStatus): List<Meeting>
    fun findAllByStatusAndReminderSentAtIsNullAndScheduledAtBetween(
        status: MeetingStatus,
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<Meeting>
}

/** 예약 회의 초대 대상 (주최자 포함) */
@Entity
@Table(name = "meeting_invitees")
class MeetingInvitee(
    @Column(nullable = false)
    val meetingId: Long,

    @Column(nullable = false)
    val userId: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invitee_id")
    var id: Long? = null
        protected set
}

interface MeetingInviteeRepository {
    fun save(invitee: MeetingInvitee): MeetingInvitee
    fun findAllByMeetingId(meetingId: Long): List<MeetingInvitee>
    fun findAllByMeetingIdIn(meetingIds: Collection<Long>): List<MeetingInvitee>
    fun findAllByUserId(userId: Long): List<MeetingInvitee>
    fun delete(invitee: MeetingInvitee)
}

enum class LeaveReason { LEFT, DISCONNECTED, KICKED, MEETING_ENDED }

/** 입장 1회 = 1행. 재입장하면 새 행 */
@Entity
@Table(name = "meeting_participants")
class MeetingParticipant(
    @Column(nullable = false)
    val meetingId: Long,

    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val displayName: String,

    val ipAddress: String? = null,
    val userAgent: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val joinedAt: LocalDateTime = LocalDateTime.now()

    var leftAt: LocalDateTime? = null
        protected set

    @Enumerated(EnumType.STRING)
    var leaveReason: LeaveReason? = null
        protected set

    fun leave(reason: LeaveReason, at: LocalDateTime = LocalDateTime.now()) {
        if (leftAt == null) {
            leftAt = at
            leaveReason = reason
        }
    }
}

interface MeetingParticipantRepository {
    fun save(participant: MeetingParticipant): MeetingParticipant
    fun findParticipantById(id: Long): MeetingParticipant?
    fun findAllByMeetingIdAndLeftAtIsNull(meetingId: Long): List<MeetingParticipant>
    fun countByMeetingIdAndLeftAtIsNull(meetingId: Long): Long
}

enum class MeetingEventType {
    MEETING_STARTED, MEETING_ENDED,
    SCREEN_SHARE_STARTED, SCREEN_SHARE_STOPPED,
    HAND_RAISED, HAND_LOWERED,
    MIC_ON, MIC_OFF, CAMERA_ON, CAMERA_OFF,
    RECORDING_STARTED, RECORDING_STOPPED,
}

@Entity
@Table(name = "meeting_events")
class MeetingEvent(
    @Column(nullable = false)
    val meetingId: Long,

    val userId: Long?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val eventType: MeetingEventType,

    val payload: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    var id: Long? = null
        protected set
}

interface MeetingEventRepository {
    fun save(event: MeetingEvent): MeetingEvent
    fun findAllByMeetingIdOrderByCreatedDtAsc(meetingId: Long): List<MeetingEvent>
}

enum class RecordingStatus { RECORDING, PROCESSING, READY, FAILED, DELETED }

@Entity
@Table(name = "meeting_recordings")
class MeetingRecording(
    @Column(nullable = false)
    val meetingId: Long,

    @Column(nullable = false)
    val startedBy: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recording_id")
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RecordingStatus = RecordingStatus.RECORDING
        protected set

    var storageUrl: String? = null
        protected set

    var fileSizeBytes: Long? = null
        protected set

    var durationSeconds: Int? = null
        protected set

    @Column(nullable = false, updatable = false)
    val startedAt: LocalDateTime = LocalDateTime.now()

    var endedAt: LocalDateTime? = null
        protected set

    val isRecording: Boolean get() = status == RecordingStatus.RECORDING

    /** 디스크에 저장한 이름 (storage_url 컬럼). 녹화를 시작할 때 한 번 정한다 */
    fun attachFile(storedName: String) {
        storageUrl = storedName
        fileSizeBytes = 0
    }

    /** 주최자 브라우저가 보낸 조각을 이어 붙였다 */
    fun appended(bytes: Long) {
        check(isRecording) { "녹화 중일 때만 이어 붙일 수 있습니다." }
        fileSizeBytes = (fileSizeBytes ?: 0) + bytes
    }

    /**
     * 녹화 끝 (주최자가 멈춤, 또는 조각이 한동안 안 와서 서버가 마무리). 받은 게 없으면 실패.
     * @param needsUpload S3 처럼 따로 올려야 하면 PROCESSING (다 올리면 [uploaded])
     */
    fun finish(durationSeconds: Int?, at: LocalDateTime = LocalDateTime.now(), needsUpload: Boolean = false) {
        if (!isRecording) return
        endedAt = at
        this.durationSeconds = durationSeconds ?: java.time.Duration.between(startedAt, at).seconds.toInt()
        status = when {
            (fileSizeBytes ?: 0) <= 0 -> RecordingStatus.FAILED
            needsUpload -> RecordingStatus.PROCESSING
            else -> RecordingStatus.READY
        }
    }

    /** S3 에 다 올림 */
    fun uploaded() {
        if (status == RecordingStatus.PROCESSING) status = RecordingStatus.READY
    }

    fun delete() {
        status = RecordingStatus.DELETED
        markDeleted()
    }
}

interface MeetingRecordingRepository {
    fun save(recording: MeetingRecording): MeetingRecording
    fun findAllByMeetingId(meetingId: Long): List<MeetingRecording>
    fun findRecordingById(id: Long): MeetingRecording?
    fun findAllByStatus(status: RecordingStatus): List<MeetingRecording>
    /** 보관 기간 정리용: 이 시각 전에 끝난 녹화 */
    fun findAllByStatusInAndEndedAtBefore(statuses: Collection<RecordingStatus>, before: LocalDateTime): List<MeetingRecording>
    /** 워크스페이스(팀) 하나의 녹화 용량 합계 (지운 것 빼고) */
    fun sumBytesByTeam(teamId: Long): Long
}

/**
 * 녹화 파일 저장소 (infrastructure/storage). 녹화 중에는 서버 디스크에 조각을 순서대로 이어 붙이고,
 * S3 를 쓰면 녹화가 끝난 뒤 [upload] 로 올린다 (그동안 상태는 PROCESSING).
 */
interface RecordingStorage {
    /** 녹화가 끝난 뒤 따로 올려야 하는지 (S3) */
    val needsUpload: Boolean
    /** 빈 파일을 만들고 저장 이름을 돌려준다 */
    fun create(): String
    /** @return 이어 붙인 바이트 수 */
    fun append(storedName: String, content: java.io.InputStream): Long
    /** 다 모은 파일을 최종 저장소로 올린다 (서버 디스크면 아무것도 안 함). 같은 이름으로 다시 불러도 된다 */
    fun upload(storedName: String)
    fun open(storedName: String, serveAs: com.webrtcmeetpro.domain.common.ServeAs): com.webrtcmeetpro.domain.common.StoredFile?
    fun delete(storedName: String)
}
