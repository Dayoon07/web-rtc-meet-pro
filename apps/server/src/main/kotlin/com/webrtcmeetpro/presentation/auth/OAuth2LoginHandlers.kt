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
 * 앱에서 시작한 구글 로그인은 끝나면 웹 대신 앱으로 돌려보낸다.
 * 돌려보낼 곳은 앱이 고르지 못하게 서버가 정한 두 가지뿐이다 (아무 주소나 받으면 코드를 남에게 넘기는 통로가 된다).
 */
object OAuthAppRedirect {
    const val SESSION_KEY = "mq.oauth.appRedirect"

    fun target(platform: String, port: Int?): String? = when (platform) {
        "android" -> "mq://oauth/callback"
        "desktop" -> port?.takeIf { it in 1024..65535 }?.let { "http://127.0.0.1:$it/oauth/callback" }
        else -> null
    }

    /** 세션을 지우기 전에 꺼내야 한다 */
    fun take(request: HttpServletRequest): String? = request.getSession(false)?.getAttribute(SESSION_KEY) as String?
}

/** 토큰이 URL·쿠키에 남지 않게 1회용 코드만 붙여 프론트 /oauth/callback (앱이면 앱) 으로 보낸다 */
@Component
class OAuth2LoginSuccessHandler(
    private val authService: AuthService,
    private val codeStore: OAuthLoginCodeStore,
    private val props: OAuth2Properties,
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
        val appRedirect = OAuthAppRedirect.take(request)
        // 세션은 구글 로그인 왕복 동안만 필요하다
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
            UriComponentsBuilder.fromUriString(appRedirect ?: props.successRedirect)
                .queryParam("code", codeStore.issue(userId))
        } catch (e: BusinessException) {
            failureUri(e.errorCode.name, appRedirect)
        } catch (e: Exception) {
            log.error("구글 로그인 처리 실패", e)
            failureUri("OAUTH_FAILED", appRedirect)
        }
        response.sendRedirect(target.build().encode().toUriString())
    }

    private fun failureUri(error: String, appRedirect: String?) =
        UriComponentsBuilder.fromUriString(appRedirect ?: props.failureRedirect).queryParam("error", error)
}

@Component
class OAuth2LoginFailureHandler(private val props: OAuth2Properties) : AuthenticationFailureHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationFailure(request: HttpServletRequest, response: HttpServletResponse, exception: AuthenticationException) {
        log.info("구글 인증 실패: {}", exception.message)
        val appRedirect = OAuthAppRedirect.take(request)
        request.getSession(false)?.invalidate()
        val uri = UriComponentsBuilder.fromUriString(appRedirect ?: props.failureRedirect).queryParam("error", "OAUTH_FAILED")
        response.sendRedirect(uri.build().encode().toUriString())
    }
}
