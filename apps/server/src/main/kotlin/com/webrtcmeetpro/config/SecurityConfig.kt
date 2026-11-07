package com.webrtcmeetpro.config

import com.webrtcmeetpro.application.admin.SystemAdminAccess
import com.webrtcmeetpro.config.properties.CorsProperties
import com.webrtcmeetpro.config.properties.OAuth2Properties
import com.webrtcmeetpro.infrastructure.security.JwtTokenProvider
import com.webrtcmeetpro.presentation.auth.OAuth2LoginFailureHandler
import com.webrtcmeetpro.presentation.auth.OAuth2LoginSuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    private val jwtTokenProvider: JwtTokenProvider,
    private val corsProperties: CorsProperties,
    private val oauth2Properties: OAuth2Properties,
    private val oauth2SuccessHandler: OAuth2LoginSuccessHandler,
    private val oauth2FailureHandler: OAuth2LoginFailureHandler,
    private val systemAdmins: SystemAdminAccess,
) {

    /**
     * /api/admin 아래 전부: 로그인한 사용자 중 시스템 관리자(users.system_role = ADMIN)만.
     * 토큰이 아니라 매 요청 DB 에서 확인하므로 권한을 회수하면 바로 막힌다. 서비스도 한 번 더 확인한다.
     */
    private val systemAdminOnly = AuthorizationManager<RequestAuthorizationContext> { authentication, _ ->
        val userId = (authentication.get()?.principal as? Jwt)?.subject?.toLongOrNull()
        AuthorizationDecision(userId != null && systemAdmins.isSystemAdmin(userId))
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            // API 는 JWT 무상태. 구글 로그인 왕복 동안만 인가 요청(state) 저장용 세션을 쓴다
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                // /ws: STOMP CONNECT 프레임에서 JWT 검증, /internal: 사이드카 공유 비밀 헤더로 검증
                it.requestMatchers("/api/auth/**", "/api/health", "/oauth2/**", "/login/oauth2/**", "/ws/**", "/internal/**").permitAll()
                    // 채팅 첨부파일: 토큰 대신 주소의 서명(exp, sig)으로 확인 (<img src> 는 헤더를 못 보낸다)
                    .requestMatchers(HttpMethod.GET, "/api/plans", "/uploads/**", "/api/chat/files/*").permitAll()
                    // 화면 오류 신고: 로그인 전 화면에서도 보낸다 (토큰이 있으면 사용자 id 도 남김)
                    .requestMatchers(HttpMethod.POST, "/api/client-errors").permitAll()
                    .requestMatchers("/api/admin/**").access(systemAdminOnly)
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { } }
            // oauth2Login 을 켜도 API 미인증 요청은 로그인 페이지 리다이렉트(302)가 아니라 401
            .exceptionHandling {
                it.authenticationEntryPoint(BearerTokenAuthenticationEntryPoint())
                // 권한 없음(관리자 API 등)도 다른 API 와 같은 JSON 에러 형식으로
                it.accessDeniedHandler { _, response, _ ->
                    response.status = 403
                    response.contentType = "application/json;charset=UTF-8"
                    response.writer.write("""{"code":"FORBIDDEN","message":"권한이 없습니다.","params":{}}""")
                }
            }

        oauth2Properties.googleRegistration()?.let { google ->
            http.oauth2Login {
                it.clientRegistrationRepository(InMemoryClientRegistrationRepository(google))
                it.successHandler(oauth2SuccessHandler)
                it.failureHandler(oauth2FailureHandler)
            }
        }
        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder = jwtTokenProvider.decoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }
}
