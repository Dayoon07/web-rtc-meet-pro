package com.webrtcmeetpro.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.concurrent.Semaphore

/**
 * SecurityConfig 와 분리: SecurityConfig → OAuth2 성공 핸들러 → AuthService → PasswordEncoder
 * 로 이어지는 순환 참조를 피하기 위해 별도 설정 클래스에 둔다.
 */
@Configuration
class PasswordConfig {
    /** 동시에 돌 비밀번호 해시 수. 비우면(0) CPU 수 × 2 */
    @Bean
    fun passwordEncoder(@Value("\${app.security.max-concurrent-password-hashes:0}") maxConcurrent: Int): PasswordEncoder =
        ThrottledPasswordEncoder(BCryptPasswordEncoder(), maxConcurrent.takeIf { it > 0 } ?: (Runtime.getRuntime().availableProcessors() * 2))
}

/**
 * 비밀번호 해시(bcrypt)는 한 번에 CPU 를 수십 ms 쓴다. 로그인이 몰려 수백 개가 한꺼번에 돌면 CPU 2개를 다 같이 나눠 써서
 * 모두가 함께 느려지고 다른 요청까지 멈춘다 (부하 테스트: 10초에 300명 로그인 → 서버 대기 줄이 차서 연결 거절).
 * 그래서 동시에 permits 개만 돌리고 나머지는 차례를 기다린다 (DB 연결은 잡지 않는다).
 */
class ThrottledPasswordEncoder(private val delegate: PasswordEncoder, permits: Int) : PasswordEncoder {
    private val gate = Semaphore(permits, true)

    private inline fun <T> limited(block: () -> T): T {
        gate.acquire()
        try {
            return block()
        } finally {
            gate.release()
        }
    }

    override fun encode(rawPassword: CharSequence?): String? = limited { delegate.encode(rawPassword) }

    override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean = limited { delegate.matches(rawPassword, encodedPassword) }

    override fun upgradeEncoding(encodedPassword: String?): Boolean = delegate.upgradeEncoding(encodedPassword)
}
