package com.webrtcmeetpro.application.meeting

import com.webrtcmeetpro.application.plan.PlanService
import com.webrtcmeetpro.application.team.TeamAccess
import com.webrtcmeetpro.domain.common.ServeAs
import com.webrtcmeetpro.domain.common.StoredFile
import com.webrtcmeetpro.domain.team.TeamRepository
import org.slf4j.LoggerFactory
import jakarta.annotation.PreDestroy
import java.util.concurrent.Executors
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.properties.JwtProperties
import com.webrtcmeetpro.domain.meeting.MeetingParticipantRepository
import com.webrtcmeetpro.domain.meeting.MeetingRecording
import com.webrtcmeetpro.domain.meeting.MeetingRecordingRepository
import com.webrtcmeetpro.domain.meeting.MeetingRepository
import com.webrtcmeetpro.domain.meeting.RecordingStatus
import com.webrtcmeetpro.domain.meeting.RecordingStorage
import com.webrtcmeetpro.domain.team.TeamPermission
import com.webrtcmeetpro.domain.user.UserRepository
import com.webrtcmeetpro.infrastructure.realtime.RealtimePublisher
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.io.InputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class RecordingView(
    val id: Long,
    val meetingId: Long,
    val startedBy: MeetingUserView?,
    val status: RecordingStatus,
    val sizeBytes: Long,
    val durationSeconds: Int?,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    /** 서명된 재생·내려받기 주소 (READY 일 때만) */
    val url: String?,
    /** 이 사람이 지울 수 있는지 (녹화한 사람, 주최자, 팀 관리자) */
    val canDelete: Boolean,
)

/** /user/queue/meeting 으로 회의실 사람들에게: 녹화 시작·끝 (회의실에 "녹화 중" 표시) */
data class MeetingRoomEvent(val type: String, val meetingId: Long, val recording: Boolean, val by: String? = null)

data class RecordingFile(val file: StoredFile, val name: String, val size: Long, val expiresAt: Long)

/** 녹화 재생 주소 서명 (?exp=&sig=). <video src> 는 토큰을 못 보내므로 볼 수 있는 사람에게만 서명된 주소를 준다 */
@Component
class RecordingUrlSigner(props: JwtProperties) {
    private val key = SecretKeySpec("recording:${props.secret}".toByteArray(), "HmacSHA256")

    fun url(recordingId: Long, now: Instant = Instant.now()): String {
        // 6시간 단위로 끊어 같은 구간 안에서는 주소가 바뀌지 않게 (브라우저 캐시·이어보기)
        val exp = (now.epochSecond / WINDOW + 2) * WINDOW
        return "/api/recordings/$recordingId/file?exp=$exp&sig=${sign(recordingId, exp)}"
    }

    fun isValid(recordingId: Long, exp: Long, sig: String, now: Instant = Instant.now()) =
        now.epochSecond <= exp && MessageDigest.isEqual(sign(recordingId, exp).toByteArray(), sig.toByteArray())

    private fun sign(id: Long, exp: Long): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("$id:$exp".toByteArray()))
    }

    companion object {
        const val WINDOW = 6 * 60 * 60L
    }
}

/**
 * 회의 녹화 (주최자 브라우저 녹화 방식).
 * 주최자(또는 팀 OWNER·ADMIN) 브라우저가 회의 탭을 MediaRecorder 로 녹화해서 10초마다 조각을 올리고, 서버는 순서대로 이어 붙인다.
 * 주최자가 창을 닫아 멈춤 신호가 오지 않아도, 조각이 2분 동안 안 오면 서버가 그때까지 받은 것으로 마무리한다.
 * 플랜: 워크스페이스(팀) 플랜에 녹화가 포함돼야 한다 (plans.recording_enabled). 워크스페이스의 녹화를 합쳐
 * plans.recording_storage_gb 까지 저장할 수 있고, plans.recording_retention_days 가 지나면 자동으로 지운다.
 * S3 를 쓰면 녹화가 끝난 뒤 올리는 동안 PROCESSING 이고, 다 올리면 READY.
 */
@Service
@Transactional(readOnly = true)
class RecordingService(
    private val recordings: MeetingRecordingRepository,
    private val meetings: MeetingRepository,
    private val participants: MeetingParticipantRepository,
    private val meetingService: MeetingService,
    private val teamAccess: TeamAccess,
    private val users: UserRepository,
    private val storage: RecordingStorage,
    private val signer: RecordingUrlSigner,
    private val realtime: RealtimePublisher,
    private val planService: PlanService,
    private val teams: TeamRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    /** S3 에 올리는 중인 녹화 (같은 녹화를 두 번 올리지 않게) */
    private val uploading = ConcurrentHashMap.newKeySet<Long>()

    /** 녹화 올리기 전용 (큰 파일이 메일·에러 기록 등 다른 비동기 작업을 붙잡지 않게 따로 2개) */
    private val executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "recording-upload").apply { isDaemon = true } }

    @PreDestroy
    fun shutdown() = executor.shutdown()

    /** 녹화별 다음에 받을 조각 번호 (재시도로 같은 조각이 두 번 와도 한 번만 붙인다). 서버 재시작 후에는 처음 오는 번호부터 */
    private val nextSeq = ConcurrentHashMap<Long, Int>()

    @Transactional
    fun start(meetingId: Long, userId: Long): RecordingView {
        val meeting = meetings.findMeetingById(meetingId) ?: throw BusinessException(ErrorCode.MEETING_NOT_FOUND)
        teamAccess.requireMember(meeting.teamId, userId)
        if (!meeting.isOpen) throw BusinessException(ErrorCode.MEETING_NOT_JOINABLE)
        if (!meetingService.canManage(meeting, userId)) throw BusinessException(ErrorCode.NOT_RECORDING_HOST)
        if (!meetingService.recordingAvailable(meeting)) throw BusinessException(ErrorCode.RECORDING_NOT_ALLOWED)
        if (recordings.findAllByMeetingId(meetingId).any { it.isRecording }) throw BusinessException(ErrorCode.RECORDING_IN_PROGRESS)
        requireStorage(meeting.teamId, 0)

        val stored = storage.create()
        // DB 저장이 실패하면 만든 빈 파일도 지운다
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) storage.delete(stored)
            }
        })
        val recording = recordings.save(MeetingRecording(meetingId, userId).apply { attachFile(stored) })
        nextSeq[recording.id!!] = 0
        broadcast(meetingId, recording = true, by = users.findUserById(userId)?.nickname)
        return view(recording, userId)
    }

    /** 조각 이어 붙이기. 녹화를 시작한 사람만, 0 부터 차례대로 */
    @Transactional
    fun append(meetingId: Long, recordingId: Long, userId: Long, seq: Int, size: Long, content: () -> InputStream): RecordingView {
        val recording = findRecording(meetingId, recordingId)
        if (recording.startedBy != userId) throw BusinessException(ErrorCode.NOT_RECORDING_HOST)
        if (!recording.isRecording) throw BusinessException(ErrorCode.RECORDING_NOT_FOUND)
        val expected = nextSeq.getOrPut(recordingId) { seq }
        when {
            seq < expected -> return view(recording, userId) // 재시도로 다시 온 조각: 이미 붙였다
            seq > expected -> throw BusinessException(ErrorCode.RECORDING_CHUNK_OUT_OF_ORDER, mapOf("expected" to expected))
        }
        if ((recording.fileSizeBytes ?: 0) + size > MAX_BYTES) throw BusinessException(ErrorCode.RECORDING_TOO_LARGE)
        meetings.findMeetingById(meetingId)?.let { requireStorage(it.teamId, size) }
        val written = content().use { storage.append(recording.storageUrl!!, it) }
        recording.appended(written)
        nextSeq[recordingId] = seq + 1
        return view(recording, userId)
    }

    @Transactional
    fun finish(meetingId: Long, recordingId: Long, userId: Long, durationSeconds: Int?): RecordingView {
        val recording = findRecording(meetingId, recordingId)
        if (recording.startedBy != userId) throw BusinessException(ErrorCode.NOT_RECORDING_HOST)
        if (recording.isRecording) {
            recording.finish(durationSeconds?.coerceIn(0, MAX_SECONDS), needsUpload = storage.needsUpload)
            nextSeq.remove(recordingId)
            broadcast(meetingId, recording = false)
            uploadAfterCommit(recording)
        }
        return view(recording, userId)
    }

    /** 회의의 녹화 목록 (팀원이면 볼 수 있다). 지운 것은 빼고, 최신 순 */
    fun list(meetingId: Long, userId: Long): List<RecordingView> {
        val meeting = meetings.findMeetingById(meetingId) ?: throw BusinessException(ErrorCode.MEETING_NOT_FOUND)
        teamAccess.requireMember(meeting.teamId, userId)
        return recordings.findAllByMeetingId(meetingId)
            .filter { it.status != RecordingStatus.DELETED && !it.deleted }
            .sortedByDescending { it.startedAt }
            .map { view(it, userId) }
    }

    /** 지우기: 녹화한 사람, 회의 주최자, 팀 OWNER·ADMIN. 파일도 지운다 */
    @Transactional
    fun delete(meetingId: Long, recordingId: Long, userId: Long) {
        val recording = findRecording(meetingId, recordingId)
        val meeting = meetings.findMeetingById(meetingId)!!
        teamAccess.requireMember(meeting.teamId, userId)
        if (!canDelete(recording, userId)) throw BusinessException(ErrorCode.NOT_RECORDING_HOST)
        val wasRecording = recording.isRecording
        recording.delete()
        nextSeq.remove(recordingId)
        recording.storageUrl?.let { name ->
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = storage.delete(name)
            })
        }
        if (wasRecording) broadcast(meetingId, recording = false)
    }

    /** 서명된 주소로 재생·내려받기 */
    fun openFile(recordingId: Long, exp: Long, sig: String, download: Boolean = false): RecordingFile {
        if (!signer.isValid(recordingId, exp, sig)) throw BusinessException(ErrorCode.RECORDING_NOT_FOUND)
        val recording = recordings.findRecordingById(recordingId)
            ?.takeIf { it.status == RecordingStatus.READY && !it.deleted } ?: throw BusinessException(ErrorCode.RECORDING_NOT_FOUND)
        val meeting = meetings.findMeetingById(recording.meetingId)
        val name = "${meeting?.title ?: "meeting"}-${recording.startedAt.toLocalDate()}.webm".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = recording.storageUrl?.let { storage.open(it, ServeAs(name, "video/webm", attachment = download)) }
            ?: throw BusinessException(ErrorCode.RECORDING_NOT_FOUND)
        return RecordingFile(file, name, recording.fileSizeBytes ?: 0, exp)
    }

    /** 조각이 2분 넘게 안 온 녹화 = 주최자가 창을 닫음 → 받은 데까지로 마무리 (스케줄러) */
    @Transactional
    fun finishStale(now: LocalDateTime = LocalDateTime.now()): Int {
        val stale = recordings.findAllByStatus(RecordingStatus.RECORDING).filter { it.editedDt.isBefore(now.minus(STALE_AFTER)) }
        stale.forEach {
            it.finish(null, it.editedDt, needsUpload = storage.needsUpload)
            nextSeq.remove(it.id!!)
            broadcast(it.meetingId, recording = false)
            uploadAfterCommit(it)
        }
        return stale.size
    }

    /** 올리다 실패했거나 서버가 재시작돼서 PROCESSING 에 남은 녹화를 다시 올린다 (스케줄러) */
    fun uploadPending(): Int {
        val pending = recordings.findAllByStatus(RecordingStatus.PROCESSING).filter { it.id !in uploading }
        pending.forEach { upload(it.id!!, it.storageUrl) }
        return pending.size
    }

    /**
     * 보관 기간이 지난 녹화 지우기 (스케줄러). 기간은 워크스페이스의 "지금" 플랜 기준 —
     * 플랜이 끝나 녹화가 없는 플랜이 되면 그 플랜의 보관 일수(기본 30일)가 지난 것부터 지운다.
     */
    @Transactional
    fun purgeExpired(now: LocalDateTime = LocalDateTime.now()): Int {
        val minDays = planService.listPlans().minOfOrNull { it.recordingRetentionDays } ?: return 0
        val candidates = recordings.findAllByStatusInAndEndedAtBefore(
            listOf(RecordingStatus.READY, RecordingStatus.FAILED), now.minusDays(minDays.toLong()),
        )
        val retentionByTeam = mutableMapOf<Long, Int>()
        var purged = 0
        candidates.forEach { r ->
            val teamId = meetings.findMeetingById(r.meetingId)?.teamId ?: return@forEach
            val days = retentionByTeam.getOrPut(teamId) { planService.planOfTeam(teamId, now).recordingRetentionDays }
            if (r.endedAt!!.isBefore(now.minusDays(days.toLong()))) {
                r.delete()
                r.storageUrl?.let { name ->
                    TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                        override fun afterCommit() = storage.delete(name)
                    })
                }
                purged++
            }
        }
        return purged
    }

    /** 워크스페이스 플랜의 녹화 저장 공간이 남았는지 */
    private fun requireStorage(teamId: Long, adding: Long) {
        val plan = planService.planOfTeam(teamId)
        val used = recordings.sumBytesByTeam(teamId)
        // 시작할 때(adding = 0)는 이미 꽉 찼으면, 조각을 붙일 때는 넘치면 막는다
        if (used + adding > plan.recordingStorageBytes || used >= plan.recordingStorageBytes) {
            throw BusinessException(ErrorCode.RECORDING_STORAGE_FULL, mapOf("gb" to plan.recordingStorageGb))
        }
    }

    private fun uploadAfterCommit(recording: MeetingRecording) {
        if (recording.status != RecordingStatus.PROCESSING) return
        val id = recording.id!!
        val name = recording.storageUrl
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = upload(id, name)
        })
    }

    /** 다른 스레드에서 S3 에 올리고, 끝나면 READY */
    private fun upload(recordingId: Long, storedName: String?) {
        if (storedName == null || !uploading.add(recordingId)) return
        executor.execute {
            try {
                storage.upload(storedName)
                tx.executeWithoutResult { recordings.findRecordingById(recordingId)?.uploaded() }
                log.info("녹화 {} 올리기 끝", recordingId)
            } catch (e: Exception) {
                log.error("녹화 {} 올리기 실패 (스케줄러가 다시 시도)", recordingId, e)
            } finally {
                uploading.remove(recordingId)
            }
        }
    }

    private fun findRecording(meetingId: Long, recordingId: Long) =
        recordings.findRecordingById(recordingId)?.takeIf { it.meetingId == meetingId && !it.deleted && it.status != RecordingStatus.DELETED }
            ?: throw BusinessException(ErrorCode.RECORDING_NOT_FOUND)

    private fun canDelete(recording: MeetingRecording, userId: Long): Boolean {
        if (recording.startedBy == userId) return true
        val meeting = meetings.findMeetingById(recording.meetingId) ?: return false
        return meeting.hostId == userId || teamAccess.can(meeting.teamId, userId, TeamPermission.CANCEL_ANY_MEETING)
    }

    /** 회의실에 지금 있는 사람들에게 녹화 상태를 알린다 */
    private fun broadcast(meetingId: Long, recording: Boolean, by: String? = null) {
        val inRoom = participants.findAllByMeetingIdAndLeftAtIsNull(meetingId).map { it.userId }.distinct()
        if (inRoom.isNotEmpty()) realtime.push(inRoom, RealtimePublisher.MEETING, MeetingRoomEvent("RECORDING", meetingId, recording, by))
    }

    private fun view(r: MeetingRecording, viewerId: Long) = RecordingView(
        id = r.id!!,
        meetingId = r.meetingId,
        startedBy = users.findUserById(r.startedBy)?.let { MeetingUserView(it.id!!, it.nickname, it.profileImageUrl) },
        status = r.status,
        sizeBytes = r.fileSizeBytes ?: 0,
        durationSeconds = r.durationSeconds,
        startedAt = r.startedAt,
        endedAt = r.endedAt,
        url = if (r.status == RecordingStatus.READY) signer.url(r.id!!) else null,
        canDelete = canDelete(r, viewerId),
    )

    companion object {
        /** 녹화 하나 최대 4GB (약 1.5Mbps 로 5~6시간) */
        const val MAX_BYTES = 4L * 1024 * 1024 * 1024
        const val MAX_SECONDS = 24 * 60 * 60
        val STALE_AFTER: Duration = Duration.ofMinutes(2)
    }
}
