package com.webrtcmeetpro.application.auth

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * 토큰이 URL·브라우저 기록에 남지 않게 리다이렉트에는 1회용 코드만 넘기고, 프론트가 POST 로 교환한다.
 * 메모리에 들고 있으므로 서버를 여러 대로 늘리면 Redis 등으로 옮겨야 한다.
 */
@Component
class OAuthLoginCodeStore {

    private data class Entry(val userId: Long, val expiresAt: Instant)

    private val codes = ConcurrentHashMap<String, Entry>()
    private val random = SecureRandom()

    fun issue(userId: Long): String {
        val now = Instant.now()
        codes.entries.removeIf { it.value.expiresAt.isBefore(now) }
        val code = ByteArray(32).also(random::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        codes[code] = Entry(userId, now.plus(TTL))
        return code
    }

    fun consume(code: String): Long? =
        codes.remove(code)?.takeIf { it.expiresAt.isAfter(Instant.now()) }?.userId

    companion object {
        private val TTL: Duration = Duration.ofSeconds(60)
    }
}
