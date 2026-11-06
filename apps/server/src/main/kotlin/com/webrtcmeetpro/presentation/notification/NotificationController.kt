package com.webrtcmeetpro.presentation.notification

import com.webrtcmeetpro.common.web.userId
import com.webrtcmeetpro.application.notification.NotificationService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notifications")
class NotificationController(private val service: NotificationService) {

    @GetMapping
    fun recent(@AuthenticationPrincipal jwt: Jwt) = service.recent(jwt.userId)

    @GetMapping("/unread-count")
    fun unreadCount(@AuthenticationPrincipal jwt: Jwt) = mapOf("count" to service.unreadCount(jwt.userId))

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun read(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = service.markRead(jwt.userId, id)

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun readAll(@AuthenticationPrincipal jwt: Jwt) = service.markAllRead(jwt.userId)
}
