package com.webrtcmeetpro.application.auth

import com.webrtcmeetpro.domain.auth.dto.res.AuthTokensResDto
import com.webrtcmeetpro.domain.auth.LoginFailureReason
import com.webrtcmeetpro.domain.auth.LoginMethod
import com.webrtcmeetpro.domain.auth.RefreshToken
import com.webrtcmeetpro.domain.auth.RefreshTokenRepository
import com.webrtcmeetpro.domain.auth.SocialAccount
import com.webrtcmeetpro.domain.auth.SocialAccountRepository
import com.webrtcmeetpro.domain.auth.SocialProvider
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.properties.JwtProperties
import com.webrtcmeetpro.infrastructure.security.JwtTokenProvider
import com.webrtcmeetpro.common.web.ClientInfo
import com.webrtcmeetpro.application.plan.PlanService
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.domain.user.UserProfile
import com.webrtcmeetpro.domain.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionOperations
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

data class SignUpCommand(
    val email: String,
    val password: String,
    val profile: UserProfile,
    val termsAgreed: Boolean,
    val privacyAgreed: Boolean,
    val marketingAgreed: Boolean,
)

data class LoginCommand(val email: String, val password: String)

data class GoogleUserInfo(
    val subject: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String?,
    val pictureUrl: String?,
)

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
    private val planService: PlanService,
    private val loginHistoryRecorder: LoginHistoryRecorder,
    private val emailVerification: EmailVerificationService,
    private val transactions: TransactionOperations,
) {
    private val random = SecureRandom()

    /** bcrypt 해시는 CPU 를 오래 쓰므로 트랜잭션 밖에서 만든다. 안에서 하면 가입이 몰릴 때 DB 커넥션이 모두 묶인다 */
    fun signUp(command: SignUpCommand, client: ClientInfo): AuthTokensResDto {
        if (!command.termsAgreed || !command.privacyAgreed) throw BusinessException(ErrorCode.TERMS_NOT_AGREED)
        val email = command.email.trim().lowercase()
        requireNewEmail(email)
        val hash = passwordEncoder.encode(command.password)!!

        return transactions.execute {
            // 해시하는 사이에 같은 이메일로 가입했을 수 있다
            requireNewEmail(email)
            val user = userRepository.save(User.register(email, hash, command.profile, command.marketingAgreed))
            planService.startFreeSubscription(user.id!!)
            // 인증 전에는 받은 팀 초대를 볼 수 없다
            emailVerification.send(user)
            user.recordLogin()
            loginHistoryRecorder.success(user.id!!, email, LoginMethod.PASSWORD, client)
            issueTokens(user, client)
        }!!
    }

    /** 한 이메일에 계정 하나. 이미 있으면 어떤 방법으로 로그인하면 되는지 알려 준다 */
    private fun requireNewEmail(email: String) {
        userRepository.findByEmail(email)?.let { existing ->
            throw BusinessException(if (existing.signInMethod == "GOOGLE") ErrorCode.EMAIL_REGISTERED_WITH_GOOGLE else ErrorCode.EMAIL_ALREADY_EXISTS)
        }
    }

    /** 구글로만 가입한 계정은 현재 비밀번호 없이 만든다. 다른 기기의 로그인은 모두 끊는다 */
    @Transactional
    fun changePassword(userId: Long, currentPassword: String?, newPassword: String, client: ClientInfo): AuthTokensResDto {
        val user = userRepository.findUserById(userId)?.takeIf { it.isActive } ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val hash = user.passwordHash
        if (hash != null && (currentPassword == null || !passwordEncoder.matches(currentPassword, hash))) {
            throw BusinessException(ErrorCode.INVALID_PASSWORD)
        }
        user.changePassword(passwordEncoder.encode(newPassword)!!)
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId).forEach { it.revoke() }
        return issueTokens(user, client)
    }

    @Transactional(readOnly = true)
    fun registeredSignInMethod(email: String): String? = userRepository.findByEmail(email.trim().lowercase())?.signInMethod

    /** 비밀번호 확인(bcrypt)은 DB 커넥션 없이 하고, 기록·토큰만 짧은 트랜잭션으로 한다 (signUp 과 같은 이유) */
    fun login(command: LoginCommand, client: ClientInfo): AuthTokensResDto {
        val email = command.email.trim().lowercase()
        val user = userRepository.findByEmail(email)
            ?: fail(null, email, LoginFailureReason.USER_NOT_FOUND, ErrorCode.INVALID_CREDENTIALS, client)
        val hash = user.passwordHash
            ?: fail(user.id, email, LoginFailureReason.SOCIAL_ACCOUNT_ONLY, ErrorCode.SOCIAL_ACCOUNT_ONLY, client)
        if (!passwordEncoder.matches(command.password, hash)) {
            fail(user.id, email, LoginFailureReason.WRONG_PASSWORD, ErrorCode.INVALID_CREDENTIALS, client)
        }
        if (!user.isActive) fail(user.id, email, LoginFailureReason.USER_NOT_ACTIVE, ErrorCode.USER_NOT_ACTIVE, client)

        return transactions.execute {
            val current = userRepository.findUserById(user.id!!)?.takeIf { it.isActive }
                ?: fail(user.id, email, LoginFailureReason.USER_NOT_ACTIVE, ErrorCode.USER_NOT_ACTIVE, client)
            current.recordLogin()
            loginHistoryRecorder.success(current.id!!, email, LoginMethod.PASSWORD, client)
            issueTokens(current, client)
        }!!
    }

    /**
     * 같은 이메일의 이메일 가입 계정이 있으면 자동으로 합치지 않고 거절한다.
     * 합치면 그 이메일을 가진 구글 계정이 남의 계정에 들어갈 수 있다.
     * @return 토큰은 1회용 코드 교환 때 발급하므로 user_id 만
     */
    @Transactional
    fun loginWithGoogle(info: GoogleUserInfo, client: ClientInfo): Long {
        val email = info.email.trim().lowercase()
        val linked = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, info.subject)

        val user = if (linked != null) {
            linked.recordLogin()
            userRepository.findUserById(linked.userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        } else {
            // 이메일 소유가 확인되지 않은 구글 계정으로 기존 계정을 가로채지 못하게
            if (!info.emailVerified) {
                fail(null, email, LoginFailureReason.EMAIL_NOT_VERIFIED, ErrorCode.OAUTH_EMAIL_NOT_VERIFIED, client, LoginMethod.GOOGLE)
            }
            userRepository.findByEmail(email)?.let { existing ->
                if (!existing.isActive) {
                    fail(existing.id, email, LoginFailureReason.USER_NOT_ACTIVE, ErrorCode.USER_NOT_ACTIVE, client, LoginMethod.GOOGLE)
                }
                fail(existing.id, email, LoginFailureReason.EMAIL_REGISTERED, ErrorCode.EMAIL_REGISTERED_WITH_PASSWORD, client, LoginMethod.GOOGLE)
            }
            val user = userRepository.save(User.registerSocial(email, info.name, info.pictureUrl))
                .also { planService.startFreeSubscription(it.id!!) }
            socialAccountRepository.save(
                SocialAccount(user.id!!, SocialProvider.GOOGLE, info.subject, email).apply { recordLogin() },
            )
            user
        }

        if (!user.isActive) {
            fail(user.id, email, LoginFailureReason.USER_NOT_ACTIVE, ErrorCode.USER_NOT_ACTIVE, client, LoginMethod.GOOGLE)
        }
        user.recordLogin()
        loginHistoryRecorder.success(user.id!!, email, LoginMethod.GOOGLE, client)
        return user.id!!
    }

    @Transactional
    fun issueTokensFor(userId: Long, client: ClientInfo): AuthTokensResDto {
        val user = userRepository.findUserById(userId)?.takeIf { it.isActive }
            ?: throw BusinessException(ErrorCode.OAUTH_CODE_INVALID)
        return issueTokens(user, client)
    }

    @Transactional
    fun refresh(rawRefreshToken: String, client: ClientInfo): AuthTokensResDto {
        val stored = refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
            ?.takeIf { it.isUsable() }
            ?: throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        val user = userRepository.findUserById(stored.userId)?.takeIf { it.isActive }
            ?: throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)
        stored.revoke()
        return issueTokens(user, client)
    }

    @Transactional
    fun logout(rawRefreshToken: String) {
        refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))?.revoke()
    }

    private fun fail(
        userId: Long?,
        email: String,
        reason: LoginFailureReason,
        error: ErrorCode,
        client: ClientInfo,
        method: LoginMethod = LoginMethod.PASSWORD,
    ): Nothing {
        // 별도 트랜잭션이라 아래 예외로 롤백되지 않는다
        loginHistoryRecorder.failure(userId, email, method, reason, client)
        throw BusinessException(error)
    }

    private fun issueTokens(user: User, client: ClientInfo): AuthTokensResDto {
        val rawRefresh = ByteArray(32).also(random::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        refreshTokenRepository.save(
            RefreshToken(
                userId = user.id!!,
                tokenHash = sha256(rawRefresh),
                expiresAt = LocalDateTime.now().plus(jwtProperties.refreshTokenTtl),
                ipAddress = client.ipAddress,
                userAgent = client.userAgent,
            ),
        )
        return AuthTokensResDto(
            accessToken = jwtTokenProvider.issueAccessToken(user.id!!, user.email, user.nickname),
            refreshToken = rawRefresh,
            expiresIn = jwtTokenProvider.accessTokenTtlSeconds,
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
