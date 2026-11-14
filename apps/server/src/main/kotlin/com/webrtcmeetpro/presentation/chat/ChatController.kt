package com.webrtcmeetpro.presentation.chat

import com.webrtcmeetpro.domain.chat.dto.req.CategoryReqDto
import com.webrtcmeetpro.domain.chat.dto.req.CreateGroupReqDto
import com.webrtcmeetpro.domain.chat.dto.req.InviteMembersReqDto
import com.webrtcmeetpro.domain.chat.dto.req.MarkReadReqDto
import com.webrtcmeetpro.domain.chat.dto.req.MuteReqDto
import com.webrtcmeetpro.domain.chat.dto.req.NoticeReqDto
import com.webrtcmeetpro.domain.chat.dto.req.OpenDirectReqDto
import com.webrtcmeetpro.domain.chat.dto.req.ReactionReqDto
import com.webrtcmeetpro.domain.chat.dto.req.SendMessageReqDto
import com.webrtcmeetpro.domain.chat.dto.req.UpdateChannelReqDto
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
import org.springframework.web.bind.annotation.PutMapping
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

@RestController
@RequestMapping("/api")
class ChatController(private val chat: ChatService) {

    @GetMapping("/chat/unread-count")
    /** teams = 워크스페이스마다 (사이드바 팀 옆 숫자) */
    fun unreadCount(@AuthenticationPrincipal jwt: Jwt): Map<String, Any> {
        val byTeam = chat.unreadByTeam(jwt.userId)
        return mapOf("count" to byTeam.values.sum(), "teams" to byTeam.map { (teamId, count) -> mapOf("teamId" to teamId, "count" to count) })
    }

    @GetMapping("/teams/{teamId}/chat/rooms")
    fun rooms(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = chat.rooms(teamId, jwt.userId)

    @PostMapping("/teams/{teamId}/chat/rooms/direct")
    fun openDirect(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody req: OpenDirectReqDto) =
        chat.openDirect(teamId, jwt.userId, req.userId!!)

    @PostMapping("/teams/{teamId}/chat/rooms/group")
    @ResponseStatus(HttpStatus.CREATED)
    fun createGroup(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody req: CreateGroupReqDto) =
        chat.createGroup(teamId, jwt.userId, req.name, req.memberIds, req.categoryId)

    @PatchMapping("/chat/rooms/{roomId}")
    fun updateChannel(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @Valid @RequestBody req: UpdateChannelReqDto) =
        chat.updateChannel(roomId, jwt.userId, req.name, req.categoryId, req.moveCategory)

    @GetMapping("/teams/{teamId}/chat/categories")
    fun categories(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = chat.categories(teamId, jwt.userId)

    @PostMapping("/teams/{teamId}/chat/categories")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody req: CategoryReqDto) =
        chat.createCategory(teamId, jwt.userId, req.name)

    @PatchMapping("/chat/categories/{categoryId}")
    fun renameCategory(@AuthenticationPrincipal jwt: Jwt, @PathVariable categoryId: Long, @Valid @RequestBody req: CategoryReqDto) =
        chat.renameCategory(categoryId, jwt.userId, req.name)

    @DeleteMapping("/chat/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(@AuthenticationPrincipal jwt: Jwt, @PathVariable categoryId: Long) = chat.deleteCategory(categoryId, jwt.userId)

    @GetMapping("/chat/rooms/{roomId}")
    fun room(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long) = chat.room(roomId, jwt.userId)

    @PostMapping("/chat/rooms/{roomId}/members")
    fun invite(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @Valid @RequestBody req: InviteMembersReqDto) =
        chat.invite(roomId, jwt.userId, req.userIds)

    @PutMapping("/chat/rooms/{roomId}/notice")
    fun setNotice(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @Valid @RequestBody req: NoticeReqDto) =
        chat.setNotice(roomId, jwt.userId, req.content)

    @PostMapping("/chat/rooms/{roomId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long) = chat.leave(roomId, jwt.userId)

    @GetMapping("/chat/rooms/{roomId}/messages")
    fun messages(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @RequestParam(required = false) before: Long?) =
        chat.messages(roomId, jwt.userId, before)

    @PostMapping("/chat/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    fun send(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @Valid @RequestBody req: SendMessageReqDto) =
        chat.send(roomId, jwt.userId, req.content, req.replyToId, req.mentionIds, req.mentionAll, req.threadRootId)

    @GetMapping("/chat/messages/{messageId}/thread")
    fun thread(@AuthenticationPrincipal jwt: Jwt, @PathVariable messageId: Long) = chat.thread(messageId, jwt.userId)

    @PutMapping("/chat/rooms/{roomId}/mute")
    fun mute(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @Valid @RequestBody req: MuteReqDto) =
        chat.setMuted(roomId, jwt.userId, req.muted!!)

    @PostMapping("/chat/rooms/{roomId}/typing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun typing(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long) = chat.typing(roomId, jwt.userId)

    @PostMapping("/chat/messages/{messageId}/reactions")
    fun react(@AuthenticationPrincipal jwt: Jwt, @PathVariable messageId: Long, @Valid @RequestBody req: ReactionReqDto) =
        chat.react(messageId, jwt.userId, req.emoji)

    @GetMapping("/teams/{teamId}/chat/search")
    fun search(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @RequestParam q: String) = chat.search(teamId, jwt.userId, q)

    @PatchMapping("/chat/messages/{messageId}")
    fun edit(@AuthenticationPrincipal jwt: Jwt, @PathVariable messageId: Long, @Valid @RequestBody req: SendMessageReqDto) =
        chat.edit(messageId, jwt.userId, req.content)

    @DeleteMapping("/chat/messages/{messageId}")
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable messageId: Long) = chat.delete(messageId, jwt.userId)

    @PostMapping("/chat/rooms/{roomId}/files", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun sendFiles(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable roomId: Long,
        @RequestPart("files") files: List<MultipartFile>,
        @RequestParam(required = false) content: String?,
        @RequestParam(required = false) threadRootId: Long?,
    ) = chat.sendFiles(roomId, jwt.userId, content, files.map { f -> FileUpload(f.originalFilename, f.contentType, f.size) { f.inputStream } }, threadRootId)

    /**
     * <img src> 로 바로 쓰려고 로그인 토큰 대신 서명(exp, sig)으로 확인한다.
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
    fun read(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @Valid @RequestBody req: MarkReadReqDto) =
        chat.markRead(roomId, jwt.userId, req.messageId!!)
}
