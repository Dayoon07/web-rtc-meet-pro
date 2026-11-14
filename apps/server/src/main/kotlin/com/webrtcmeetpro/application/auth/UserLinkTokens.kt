package com.webrtcmeetpro.application.auth

import com.webrtcmeetpro.domain.user.User
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 메일 링크용 서명 토큰 "사용자ID.만료시각.서명". DB 에 저장하지 않는 대신 사용자의 지금 상태(fingerprint)를
 * 서명에 섞어, 비밀번호나 이메일이 바뀌면 이미 보낸 링크가 무효가 되게 한다.
 *
 * @param purpose 재설정 링크로 이메일 인증을 할 수 없게 용도마다 다른 키를 쓴다
 */
abstract class UserLinkTokens(secret: String, purpose: String, val ttl: Duration) {
    private val key = SecretKeySpec("$purpose:$secret".toByteArray(), "HmacSHA256")

    protected abstract fun fingerprint(user: User): String

    fun issue(user: User, now: Instant = Instant.now()): String {
        val exp = now.plus(ttl).epochSecond
        return "${user.id}.$exp.${sign(user, exp)}"
    }

    /** 서명 확인은 사용자를 읽은 뒤 [isValid] 로 */
    fun userIdOf(token: String): Long? = token.split('.').takeIf { it.size == 3 }?.get(0)?.toLongOrNull()

    fun isValid(token: String, user: User, now: Instant = Instant.now()): Boolean {
        val parts = token.split('.')
        if (parts.size != 3 || parts[0] != user.id.toString()) return false
        val exp = parts[1].toLongOrNull() ?: return false
        if (now.epochSecond > exp) return false
        return MessageDigest.isEqual(sign(user, exp).toByteArray(), parts[2].toByteArray())
    }

    private fun sign(user: User, exp: Long): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("${user.id}.$exp.${fingerprint(user)}".toByteArray()))
    }
}
