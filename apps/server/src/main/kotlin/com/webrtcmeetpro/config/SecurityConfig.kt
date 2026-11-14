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

    /** 권한을 회수하면 바로 막히도록 토큰이 아니라 매 요청 DB 에서 확인한다 */
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
            // 구글 로그인 왕복 동안만 state 저장용 세션을 쓴다
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                // /ws 는 STOMP CONNECT 에서 JWT 를, /internal 은 사이드카 공유 비밀 헤더를 따로 확인한다
                it.requestMatchers("/api/auth/**", "/api/health", "/oauth2/**", "/login/oauth2/**", "/ws/**", "/internal/**").permitAll()
                    // <img src> 는 헤더를 못 보내서 토큰 대신 주소의 서명으로 확인한다
                    .requestMatchers(HttpMethod.GET, "/api/plans", "/uploads/**", "/api/chat/files/*", "/api/recordings/*/file").permitAll()
                    // 로그인 전 화면의 오류도 받아야 한다
                    .requestMatchers(HttpMethod.POST, "/api/client-errors").permitAll()
                    .requestMatchers("/api/admin/**").access(systemAdminOnly)
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { } }
            // oauth2Login 을 켜면 미인증 요청이 로그인 페이지로 302 되므로 API 는 401 로 바꾼다
            .exceptionHandling {
                it.authenticationEntryPoint(BearerTokenAuthenticationEntryPoint())
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
