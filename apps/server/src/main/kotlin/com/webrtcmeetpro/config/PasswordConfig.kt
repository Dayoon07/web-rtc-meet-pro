package com.webrtcmeetpro.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.concurrent.Semaphore

/** SecurityConfig → OAuth2 성공 핸들러 → AuthService → PasswordEncoder 순환 참조를 피하려고 따로 둔다 */
@Configuration
class PasswordConfig {
    /** 0 이면 CPU 수 × 2 */
    @Bean
    fun passwordEncoder(@Value("\${app.security.max-concurrent-password-hashes:0}") maxConcurrent: Int): PasswordEncoder =
        ThrottledPasswordEncoder(BCryptPasswordEncoder(), maxConcurrent.takeIf { it > 0 } ?: (Runtime.getRuntime().availableProcessors() * 2))
}

/**
 * bcrypt 는 한 번에 CPU 를 수십 ms 쓰므로, 로그인이 몰려 수백 개가 한꺼번에 돌면 모든 요청이 함께 느려진다.
 * 동시에 permits 개만 돌리고 나머지는 차례를 기다리게 한다.
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
