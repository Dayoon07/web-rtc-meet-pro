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
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** 지금 비밀번호 해시를 서명에 섞어서, 한 번 쓰면(비밀번호가 바뀌면) 같은 링크는 무효가 된다 */
@Component
class PasswordResetTokens(props: JwtProperties) : UserLinkTokens(props.secret, "password-reset", TTL) {
    // 구글로만 가입해 비밀번호가 없는 계정도 재설정으로 비밀번호를 만들 수 있다
    override fun fingerprint(user: User) = "${user.email}|${user.passwordHash ?: "-"}"

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
    /** 같은 주소로 메일을 연달아 보내지 않게. 서버가 한 대라 메모리로 충분하다 */
    private val lastRequested = ConcurrentHashMap<String, Instant>()

    /** 가입 여부를 알려 주지 않으려고 계정이 없어도 똑같이 성공으로 응답한다 */
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

    /** 비밀번호가 새어 나갔을 수 있으니 다른 기기의 로그인을 모두 끊는다 */
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
