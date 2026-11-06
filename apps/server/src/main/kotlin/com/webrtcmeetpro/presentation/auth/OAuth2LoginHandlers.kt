package com.webrtcmeetpro.presentation.auth

import com.webrtcmeetpro.application.auth.AuthService
import com.webrtcmeetpro.application.auth.GoogleUserInfo
import com.webrtcmeetpro.application.auth.OAuthLoginCodeStore
import com.webrtcmeetpro.config.properties.OAuth2Properties
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.web.ClientInfo
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * 구글 인증 성공 → 사용자 조회/가입/연결 → 1회용 코드를 붙여 프론트 /oauth/callback 으로 리다이렉트.
 * (shortform 프로젝트의 OAuth2SuccessHandler 와 같은 흐름이지만, 토큰을 쿠키로 넘기지 않는다)
 */
@Component
class OAuth2LoginSuccessHandler(
    private val authService: AuthService,
    private val codeStore: OAuthLoginCodeStore,
    private val props: OAuth2Properties,
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
        // 인가 요청 저장용으로만 쓴 세션은 바로 정리 (API 는 JWT 무상태)
        request.getSession(false)?.invalidate()

        val principal = authentication.principal as OAuth2User
        val info = GoogleUserInfo(
            subject = principal.getAttribute<String>("sub") ?: principal.name,
            email = principal.getAttribute<String>("email").orEmpty(),
            emailVerified = principal.getAttribute<Any>("email_verified").let { it == true || it == "true" },
            name = principal.getAttribute<String>("name"),
            pictureUrl = principal.getAttribute<String>("picture"),
        )

        val target = try {
            require(info.email.isNotBlank()) { "구글 계정에 이메일이 없습니다." }
            val userId = authService.loginWithGoogle(info, ClientInfo.from(request))
            UriComponentsBuilder.fromUriString(props.successRedirect)
                .queryParam("code", codeStore.issue(userId))
        } catch (e: BusinessException) {
            failureUri(e.errorCode.name)
        } catch (e: Exception) {
            log.error("구글 로그인 처리 실패", e)
            failureUri("OAUTH_FAILED")
        }
        response.sendRedirect(target.build().encode().toUriString())
    }

    private fun failureUri(error: String) =
        UriComponentsBuilder.fromUriString(props.failureRedirect).queryParam("error", error)
}

@Component
class OAuth2LoginFailureHandler(private val props: OAuth2Properties) : AuthenticationFailureHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationFailure(request: HttpServletRequest, response: HttpServletResponse, exception: AuthenticationException) {
        log.info("구글 인증 실패: {}", exception.message)
        request.getSession(false)?.invalidate()
        val uri = UriComponentsBuilder.fromUriString(props.failureRedirect).queryParam("error", "OAUTH_FAILED")
        response.sendRedirect(uri.build().encode().toUriString())
    }
}
