package com.webrtcmeetpro.presentation.chat

import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.DeleteMapping
import com.webrtcmeetpro.application.chat.ChatService
import com.webrtcmeetpro.application.chat.FileUpload
import com.webrtcmeetpro.common.web.userId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import com.webrtcmeetpro.presentation.common.StoredFileResponses
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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
import java.time.Duration
import java.time.Instant

data class OpenDirectRequest(@field:NotNull val userId: Long?)
data class CreateGroupRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:Size(max = 200) val memberIds: List<Long> = emptyList(),
)
data class InviteMembersRequest(@field:Size(min = 1, max = 200) val userIds: List<Long>)
data class SendMessageRequest(@field:NotBlank @field:Size(max = 4000) val content: String)
data class MarkReadRequest(@field:NotNull val messageId: Long?)

@RestController
@RequestMapping("/api")
class ChatController(private val chat: ChatService) {

    /** 사이드바 "채팅" 배지: 모든 팀의 안 읽은 메시지 합계 */
    @GetMapping("/chat/unread-count")
    fun unreadCount(@AuthenticationPrincipal jwt: Jwt) = mapOf("count" to chat.totalUnread(jwt.userId))

    @GetMapping("/teams/{teamId}/chat/rooms")
    fun rooms(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = chat.rooms(teamId, jwt.userId)

    @PostMapping("/teams/{teamId}/chat/rooms/direct")
    fun openDirect(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody req: OpenDirectRequest) =
        chat.openDirect(teamId, jwt.userId, req.userId!!)

    @PostMapping("/teams/{teamId}/chat/rooms/group")
    @ResponseStatus(HttpStatus.CREATED)
    fun createGroup(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody req: CreateGroupRequest) =
        chat.createGroup(teamId, jwt.userId, req.name, req.memberIds)

    @GetMapping("/chat/rooms/{roomId}")
    fun room(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long) = chat.room(roomId, jwt.userId)

    @PostMapping("/chat/rooms/{roomId}/members")
    fun invite(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @Valid @RequestBody req: InviteMembersRequest) =
        chat.invite(roomId, jwt.userId, req.userIds)

    @PostMapping("/chat/rooms/{roomId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long) = chat.leave(roomId, jwt.userId)

    @GetMapping("/chat/rooms/{roomId}/messages")
    fun messages(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @RequestParam(required = false) before: Long?) =
        chat.messages(roomId, jwt.userId, before)

    @PostMapping("/chat/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    fun send(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @Valid @RequestBody req: SendMessageRequest) =
        chat.send(roomId, jwt.userId, req.content)

    /** 내가 보낸 글 메시지 고치기 */
    @PatchMapping("/chat/messages/{messageId}")
    fun edit(@AuthenticationPrincipal jwt: Jwt, @PathVariable messageId: Long, @Valid @RequestBody req: SendMessageRequest) =
        chat.edit(messageId, jwt.userId, req.content)

    /** 메시지 지우기 (보낸 사람, 또는 팀 OWNER·ADMIN). 방 사람들에게 "삭제된 메시지"로 바뀐다 */
    @DeleteMapping("/chat/messages/{messageId}")
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable messageId: Long) = chat.delete(messageId, jwt.userId)

    /** 파일 보내기 (multipart): files 여러 개 + content(함께 보낼 글, 선택) */
    @PostMapping("/chat/rooms/{roomId}/files", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun sendFiles(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable roomId: Long,
        @RequestPart("files") files: List<MultipartFile>,
        @RequestParam(required = false) content: String?,
    ) = chat.sendFiles(roomId, jwt.userId, content, files.map { f -> FileUpload(f.originalFilename, f.contentType, f.size) { f.inputStream } })

    /**
     * 첨부파일 내려받기. 로그인 토큰 대신 메시지 목록이 내려준 서명(exp, sig)으로 확인한다 (<img src> 로 바로 쓰려고).
     * 미리보기 가능한 이미지만 inline, 나머지(그리고 download=true)는 저장 대화상자로.
     * CSP sandbox: 주소를 새 탭으로 직접 열어도 이 응답 안에서는 스크립트가 돌지 않는다.
     */
    @GetMapping("/chat/files/{fileId}")
    fun download(
        @PathVariable fileId: Long,
        @RequestParam exp: Long,
        @RequestParam sig: String,
        @RequestParam(defaultValue = "false") download: Boolean,
    ): ResponseEntity<Resource> {
        val file = chat.openFile(fileId, exp, sig, download)
        val disposition = (if (file.inline) ContentDisposition.inline() else ContentDisposition.attachment())
            .filename(file.name, StandardCharsets.UTF_8)
            .build()
        val maxAge = (file.expiresAt - Instant.now().epochSecond).coerceAtLeast(0)
        return StoredFileResponses.of(file.file, maxAge) {
            it.contentType(MediaType.parseMediaType(file.contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox")
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(maxAge)).cachePrivate())
        }
    }

    @PostMapping("/chat/rooms/{roomId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun read(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @Valid @RequestBody req: MarkReadRequest) =
        chat.markRead(roomId, jwt.userId, req.messageId!!)
}
