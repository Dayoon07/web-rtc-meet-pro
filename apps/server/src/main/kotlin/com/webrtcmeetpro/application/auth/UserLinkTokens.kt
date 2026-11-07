package com.webrtcmeetpro.application.auth

import com.webrtcmeetpro.domain.user.User
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 메일 링크에 넣는 서명 토큰 (DB 에 저장하지 않는다): "사용자ID.만료시각.서명".
 * 서명에 사용자의 지금 상태(fingerprint)를 섞어서, 그 상태가 바뀌면 같은 링크는 더 이상 맞지 않는다
 * (예: 비밀번호를 바꾸면 재설정 링크가, 이메일을 바꾸면 인증 링크가 무효).
 *
 * @param purpose 용도마다 다른 키를 쓴다 (재설정 링크로 이메일 인증을 할 수 없게)
 */
abstract class UserLinkTokens(secret: String, purpose: String, val ttl: Duration) {
    private val key = SecretKeySpec("$purpose:$secret".toByteArray(), "HmacSHA256")

    /** 이 값이 바뀌면 이미 보낸 링크가 무효가 된다 */
    protected abstract fun fingerprint(user: User): String

    fun issue(user: User, now: Instant = Instant.now()): String {
        val exp = now.plus(ttl).epochSecond
        return "${user.id}.$exp.${sign(user, exp)}"
    }

    /** 토큰에서 사용자 ID 만 꺼낸다 (서명 확인은 사용자를 읽은 뒤 [isValid]) */
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
