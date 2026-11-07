package com.webrtcmeetpro.presentation.meeting

import com.webrtcmeetpro.application.meeting.RecordingService
import com.webrtcmeetpro.common.web.userId
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.time.Instant

data class FinishRecordingRequest(val durationSeconds: Int? = null)

/**
 * 회의 녹화 API. 주최자 브라우저가 녹화하면서 조각(multipart "chunk", 0 부터 seq)을 차례로 올리고, 끝나면 finish.
 */
@RestController
@RequestMapping("/api")
class RecordingController(private val recordings: RecordingService) {

    @PostMapping("/meetings/{meetingId}/recordings")
    @ResponseStatus(HttpStatus.CREATED)
    fun start(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = recordings.start(meetingId, jwt.userId)

    @GetMapping("/meetings/{meetingId}/recordings")
    fun list(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = recordings.list(meetingId, jwt.userId)

    @PostMapping("/meetings/{meetingId}/recordings/{recordingId}/chunks", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun chunk(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable meetingId: Long,
        @PathVariable recordingId: Long,
        @RequestParam seq: Int,
        @RequestPart("chunk") chunk: MultipartFile,
    ) = recordings.append(meetingId, recordingId, jwt.userId, seq, chunk.size) { chunk.inputStream }

    @PostMapping("/meetings/{meetingId}/recordings/{recordingId}/finish")
    fun finish(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable meetingId: Long,
        @PathVariable recordingId: Long,
        @RequestBody(required = false) body: FinishRecordingRequest?,
    ) = recordings.finish(meetingId, recordingId, jwt.userId, body?.durationSeconds)

    @DeleteMapping("/meetings/{meetingId}/recordings/{recordingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long, @PathVariable recordingId: Long) =
        recordings.delete(meetingId, recordingId, jwt.userId)

    /**
     * 재생·내려받기 (서명된 주소, 로그인 토큰 없이). Range 요청을 지원해서 영상 중간으로 바로 넘어갈 수 있다
     * (ResponseEntity<Resource> 는 스프링이 Range 를 알아서 처리한다).
     */
    @GetMapping("/recordings/{recordingId}/file")
    fun file(
        @PathVariable recordingId: Long,
        @RequestParam exp: Long,
        @RequestParam sig: String,
        @RequestParam(defaultValue = "false") download: Boolean,
    ): ResponseEntity<Resource> {
        val file = recordings.openFile(recordingId, exp, sig)
        val disposition = (if (download) ContentDisposition.attachment() else ContentDisposition.inline())
            .filename(file.name, StandardCharsets.UTF_8).build()
        val maxAge = (file.expiresAt - Instant.now().epochSecond).coerceAtLeast(0)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("video/webm"))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=$maxAge")
            .header("Content-Security-Policy", "default-src 'none'; media-src 'self'; sandbox")
            .body(FileSystemResource(file.path))
    }
}
