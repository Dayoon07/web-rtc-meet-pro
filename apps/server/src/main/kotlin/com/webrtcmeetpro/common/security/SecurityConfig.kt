package com.webrtcmeetpro.common.security

import com.webrtcmeetpro.auth.presentation.OAuth2LoginFailureHandler
import com.webrtcmeetpro.auth.presentation.OAuth2LoginSuccessHandler
import com.webrtcmeetpro.common.config.OAuth2Properties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
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
) {

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
                    .requestMatchers(HttpMethod.GET, "/api/plans", "/uploads/**").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { } }
            // oauth2Login 을 켜도 API 미인증 요청은 로그인 페이지 리다이렉트(302)가 아니라 401
            .exceptionHandling { it.authenticationEntryPoint(BearerTokenAuthenticationEntryPoint()) }

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
