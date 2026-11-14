package com.webrtcmeetpro.presentation.presence

import com.webrtcmeetpro.domain.presence.dto.req.PresenceReqDto
import com.webrtcmeetpro.application.presence.PresenceService
import com.webrtcmeetpro.common.web.userId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@RestController
@RequestMapping("/api")
class PresenceController(private val presence: PresenceService) {

    /** 워크스페이스에서 지금 접속 중·자리 비움인 사람 (나머지는 접속 안 함) */
    @GetMapping("/teams/{teamId}/presence")
    fun team(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = presence.team(teamId, jwt.userId)

    /** 내 상태: 창을 안 보거나 한동안 움직임이 없으면 자리 비움 */
    @PutMapping("/presence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun set(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody req: PresenceReqDto) = presence.setAway(jwt.userId, req.away!!)
}

/** 실시간 연결(STOMP)이 붙고 끊길 때 접속 상태를 바꾼다. 연결의 사용자 이름 = user_id (WebSocketConfig) */
@Component
class PresenceSessionListener(private val presence: PresenceService) {
    @EventListener
    fun onConnected(event: SessionConnectedEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val userId = event.user?.name?.toLongOrNull() ?: return
        presence.connected(userId, accessor.sessionId ?: return)
    }

    @EventListener
    fun onDisconnected(event: SessionDisconnectEvent) {
        val userId = event.user?.name?.toLongOrNull() ?: return
        presence.disconnected(userId, event.sessionId)
    }
}
