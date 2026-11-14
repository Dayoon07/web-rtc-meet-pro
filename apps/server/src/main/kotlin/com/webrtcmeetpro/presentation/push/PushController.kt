package com.webrtcmeetpro.presentation.push

import com.webrtcmeetpro.domain.push.dto.req.PushSubscribeReqDto
import com.webrtcmeetpro.domain.push.dto.req.PushUnsubscribeReqDto
import com.webrtcmeetpro.application.push.PushService
import com.webrtcmeetpro.common.web.userId
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/push")
class PushController(private val push: PushService) {

    /** 공개 키 (브라우저 구독에 필요). enabled=false 면 서버에 키가 없다 */
    @GetMapping("/config")
    fun config() = push.config()

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun subscribe(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody req: PushSubscribeReqDto, http: HttpServletRequest) =
        push.subscribe(jwt.userId, req.endpoint, req.p256dh, req.auth, req.lang, http.getHeader("User-Agent"))

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unsubscribe(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody req: PushUnsubscribeReqDto) = push.unsubscribe(jwt.userId, req.endpoint)
}
