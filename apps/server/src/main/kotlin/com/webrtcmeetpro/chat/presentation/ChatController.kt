package com.webrtcmeetpro.chat.presentation

import com.webrtcmeetpro.chat.application.ChatService
import com.webrtcmeetpro.common.web.userId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

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

    @PostMapping("/chat/rooms/{roomId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun read(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long, @Valid @RequestBody req: MarkReadRequest) =
        chat.markRead(roomId, jwt.userId, req.messageId!!)
}
