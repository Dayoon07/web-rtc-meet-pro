package com.webrtcmeetpro.application.auth

import com.webrtcmeetpro.application.mail.MailLanguage
import com.webrtcmeetpro.application.mail.MailService
import com.webrtcmeetpro.application.mail.MailTemplates
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.properties.JwtProperties
import com.webrtcmeetpro.config.properties.WebProperties
import com.webrtcmeetpro.domain.auth.RefreshTokenRepository
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.domain.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 비밀번호 재설정 링크의 토큰. DB 에 저장하지 않는다: "사용자ID.만료시각.서명".
 * 서명에 지금 비밀번호 해시의 지문을 섞어서, 비밀번호가 바뀌면(= 한 번 쓰면) 같은 링크는 더 이상 맞지 않는다.
 */
@Component
class PasswordResetTokens(props: JwtProperties) {
    private val key = SecretKeySpec("password-reset:${props.secret}".toByteArray(), "HmacSHA256")

    fun issue(user: User, now: Instant = Instant.now()): String {
        val exp = now.plus(TTL).epochSecond
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
        // 비밀번호가 없는(구글로만 가입한) 계정도 재설정으로 비밀번호를 만들 수 있다
        val fingerprint = "${user.email}|${user.passwordHash ?: "-"}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("${user.id}.$exp.$fingerprint".toByteArray()))
    }

    companion object {
        val TTL: Duration = Duration.ofMinutes(30)
    }
}

@Service
class PasswordResetService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokens: PasswordResetTokens,
    private val mail: MailService,
    private val web: WebProperties,
) {
    /** 이메일별 마지막 요청 시각 — 같은 주소로 메일을 연달아 보내지 않게 (서버 한 대 기준 메모리) */
    private val lastRequested = ConcurrentHashMap<String, Instant>()

    /**
     * 재설정 메일 요청. 가입 여부를 알려 주지 않으려고 계정이 없어도 똑같이 성공으로 응답한다.
     */
    fun request(rawEmail: String, language: MailLanguage = MailLanguage.current()) {
        val email = rawEmail.trim().lowercase()
        val now = Instant.now()
        val last = lastRequested[email]
        if (last != null && last.plus(RESEND_INTERVAL).isAfter(now)) return
        lastRequested[email] = now
        if (lastRequested.size > 10_000) lastRequested.entries.removeIf { it.value.plus(RESEND_INTERVAL).isBefore(now) }

        val user = userRepository.findByEmail(email)?.takeIf { it.isActive } ?: return
        val token = tokens.issue(user, now)
        val link = web.link("/reset-password?token=" + URLEncoder.encode(token, Charsets.UTF_8))
        mail.sendAfterCommit(MailTemplates.passwordReset(user.email, language, link, PasswordResetTokens.TTL.toMinutes()))
    }

    /** 새 비밀번호로 바꾸고, 다른 기기의 로그인(리프레시 토큰)을 모두 끊는다 */
    @Transactional
    fun confirm(token: String, newPassword: String) {
        val user = tokens.userIdOf(token)?.let { userRepository.findUserById(it) }
            ?.takeIf { it.isActive && tokens.isValid(token, it) }
            ?: throw BusinessException(ErrorCode.RESET_TOKEN_INVALID)
        user.changePassword(passwordEncoder.encode(newPassword)!!)
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.id!!).forEach { it.revoke() }
    }

    companion object {
        val RESEND_INTERVAL: Duration = Duration.ofSeconds(60)
    }
}
