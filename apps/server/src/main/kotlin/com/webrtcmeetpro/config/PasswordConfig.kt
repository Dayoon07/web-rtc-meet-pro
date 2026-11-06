package com.webrtcmeetpro.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * SecurityConfig 와 분리: SecurityConfig → OAuth2 성공 핸들러 → AuthService → PasswordEncoder
 * 로 이어지는 순환 참조를 피하기 위해 별도 설정 클래스에 둔다.
 */
@Configuration
class PasswordConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
