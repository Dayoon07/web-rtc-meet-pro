package com.webrtcmeetpro.application.auth

import com.webrtcmeetpro.application.mail.MailLanguage
import com.webrtcmeetpro.application.mail.MailService
import com.webrtcmeetpro.application.mail.MailTemplates
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.properties.JwtProperties
import com.webrtcmeetpro.config.properties.WebProperties
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.domain.user.UserRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** 가입 이메일 인증 링크의 토큰 (24시간). 이메일이 바뀌면 예전 링크는 무효 */
@Component
class EmailVerificationTokens(props: JwtProperties) : UserLinkTokens(props.secret, "email-verify", TTL) {
    override fun fingerprint(user: User) = user.email

    companion object {
        val TTL: Duration = Duration.ofHours(24)
    }
}

/**
 * 이메일 가입자가 그 이메일의 주인인지 확인한다. 인증 전에는 받은 팀 초대를 보거나 수락할 수 없다
 * (남의 이메일로 가입해서 그 사람에게 온 초대를 가로채지 못하게).
 * 구글 가입자는 구글이 이메일을 확인했으므로 처음부터 인증된 상태다.
 */
@Service
class EmailVerificationService(
    private val userRepository: UserRepository,
    private val tokens: EmailVerificationTokens,
    private val mail: MailService,
    private val web: WebProperties,
) {
    /** 사용자별 마지막 발송 시각 — 버튼을 연달아 눌러도 메일이 쏟아지지 않게 (서버 한 대 기준 메모리) */
    private val lastSent = ConcurrentHashMap<Long, Instant>()

    /** 인증 메일 보내기 (가입 직후. 트랜잭션 안이면 커밋된 뒤에 보낸다) */
    fun send(user: User, language: MailLanguage = MailLanguage.current()) {
        val link = web.link("/verify-email/confirm?token=" + URLEncoder.encode(tokens.issue(user), Charsets.UTF_8))
        lastSent[user.id!!] = Instant.now()
        mail.sendAfterCommit(MailTemplates.emailVerification(user.email, language, link, EmailVerificationTokens.TTL.toHours()))
    }

    /** "인증 메일 다시 보내기". 이미 인증했으면 아무것도 하지 않는다 */
    @Transactional(readOnly = true)
    fun resend(userId: Long) {
        val user = userRepository.findUserById(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        if (user.isEmailVerified) return
        val waited = lastSent[userId]?.let { Duration.between(it, Instant.now()) }
        if (waited != null && waited < RESEND_INTERVAL) {
            throw BusinessException(ErrorCode.RESEND_TOO_SOON, mapOf("seconds" to (RESEND_INTERVAL - waited).seconds.coerceAtLeast(1)))
        }
        send(user)
    }

    /** 메일 링크로 들어왔을 때. 로그인하지 않은 기기(휴대폰 메일 앱 등)에서 열어도 된다 */
    @Transactional
    fun confirm(token: String) {
        val user = tokens.userIdOf(token)?.let { userRepository.findUserById(it) }
            ?.takeIf { it.isActive && tokens.isValid(token, it) }
            ?: throw BusinessException(ErrorCode.EMAIL_VERIFY_TOKEN_INVALID)
        user.verifyEmail()
    }

    companion object {
        val RESEND_INTERVAL: Duration = Duration.ofSeconds(60)
    }
}
