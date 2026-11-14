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
 * 앱(Android·Windows)에서 로그인한 상태 그대로 브라우저 페이지(예: 화상회의)를 열 때 쓴다.
 * 구글 로그인과 같은 60초짜리 1회용 코드를 주고, 브라우저는 /oauth/callback?code=…&next=… 에서
 * POST /api/auth/oauth/exchange 로 교환한다. 토큰 자체는 URL 에 싣지 않는다.
 * /api/auth 아래는 비로그인 허용 경로라서 인증이 필요한 이 API 는 따로 둔다.
 */
@RestController
@RequestMapping("/api/sessions")
class WebHandoffController(private val codeStore: OAuthLoginCodeStore) {

    @PostMapping("/web-handoff")
    fun handoff(@AuthenticationPrincipal jwt: Jwt) = WebHandoffResDto(codeStore.issue(jwt.userId))
}
