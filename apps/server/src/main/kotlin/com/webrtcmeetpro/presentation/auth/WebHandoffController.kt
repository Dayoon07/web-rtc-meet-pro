package com.webrtcmeetpro.presentation.auth

import com.webrtcmeetpro.domain.auth.dto.res.WebHandoffResDto
import com.webrtcmeetpro.application.auth.OAuthLoginCodeStore
import com.webrtcmeetpro.common.web.userId
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 앱에서 로그인한 상태 그대로 브라우저 페이지(화상회의 등)를 열 때 토큰을 URL 에 싣지 않으려고 1회용 코드를 준다.
 * /api/auth 아래는 비로그인 허용 경로라 인증이 필요한 이 API 는 따로 둔다.
 */
@RestController
@RequestMapping("/api/sessions")
class WebHandoffController(private val codeStore: OAuthLoginCodeStore) {

    @PostMapping("/web-handoff")
    fun handoff(@AuthenticationPrincipal jwt: Jwt) = WebHandoffResDto(codeStore.issue(jwt.userId))
}
