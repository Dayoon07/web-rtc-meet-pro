package com.webrtcmeetpro.application.auth

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

/** 구글 OIDC 사용자 정보 (sub / email / email_verified / name / picture) */
data class GoogleUserInfo(
    val subject: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String?,
    val pictureUrl: String?,
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
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
) {
    private val random = SecureRandom()

    @Transactional
    fun signUp(command: SignUpCommand, client: ClientInfo): AuthTokens {
        if (!command.termsAgreed || !command.privacyAgreed) throw BusinessException(ErrorCode.TERMS_NOT_AGREED)
        val email = command.email.trim().lowercase()
        if (userRepository.existsByEmail(email)) throw BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS)

        val user = userRepository.save(
            User.register(email, passwordEncoder.encode(command.password)!!, command.profile, command.marketingAgreed),
        )
        planService.startFreeSubscription(user.id!!)
        user.recordLogin()
        loginHistoryRecorder.success(user.id!!, email, LoginMethod.PASSWORD, client)
        return issueTokens(user, client)
    }

    @Transactional(readOnly = true)
    fun isEmailAvailable(email: String): Boolean = !userRepository.existsByEmail(email.trim().lowercase())

    @Transactional
    fun login(command: LoginCommand, client: ClientInfo): AuthTokens {
        val email = command.email.trim().lowercase()
        val user = userRepository.findByEmail(email)
            ?: fail(null, email, LoginFailureReason.USER_NOT_FOUND, ErrorCode.INVALID_CREDENTIALS, client)
        val hash = user.passwordHash
            ?: fail(user.id, email, LoginFailureReason.SOCIAL_ACCOUNT_ONLY, ErrorCode.SOCIAL_ACCOUNT_ONLY, client)
        if (!passwordEncoder.matches(command.password, hash)) {
            fail(user.id, email, LoginFailureReason.WRONG_PASSWORD, ErrorCode.INVALID_CREDENTIALS, client)
        }
        if (!user.isActive) fail(user.id, email, LoginFailureReason.USER_NOT_ACTIVE, ErrorCode.USER_NOT_ACTIVE, client)

        user.recordLogin()
        loginHistoryRecorder.success(user.id!!, email, LoginMethod.PASSWORD, client)
        return issueTokens(user, client)
    }

    /**
     * 구글 로그인. 연결된 계정 → 같은 이메일의 기존 계정에 연결 → 신규 가입 순서로 찾는다.
     * 신규 가입자는 실명·전화번호 등이 비어 있으므로 프론트가 온보딩 화면으로 보낸다.
     * @return 로그인한 user_id (토큰은 1회용 코드 교환 시 발급)
     */
    @Transactional
    fun loginWithGoogle(info: GoogleUserInfo, client: ClientInfo): Long {
        val email = info.email.trim().lowercase()
        val linked = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, info.subject)

        val user = if (linked != null) {
            linked.recordLogin()
            userRepository.findUserById(linked.userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        } else {
            // 이메일 소유가 확인되지 않은 구글 계정으로 기존 계정을 가로채지 못하게 막는다
            if (!info.emailVerified) {
                fail(null, email, LoginFailureReason.EMAIL_NOT_VERIFIED, ErrorCode.OAUTH_EMAIL_NOT_VERIFIED, client, LoginMethod.GOOGLE)
            }
            val user = userRepository.findByEmail(email)
                ?: userRepository.save(User.registerSocial(email, info.name, info.pictureUrl))
                    .also { planService.startFreeSubscription(it.id!!) }
            if (user.profileImageUrl == null && info.pictureUrl != null) user.changeProfileImage(info.pictureUrl)
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
    fun issueTokensFor(userId: Long, client: ClientInfo): AuthTokens {
        val user = userRepository.findUserById(userId)?.takeIf { it.isActive }
            ?: throw BusinessException(ErrorCode.OAUTH_CODE_INVALID)
        return issueTokens(user, client)
    }

    /** 리프레시 토큰 회전: 기존 토큰은 폐기하고 새 쌍을 발급 */
    @Transactional
    fun refresh(rawRefreshToken: String, client: ClientInfo): AuthTokens {
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
        // 별도 트랜잭션으로 커밋되므로 아래 예외로 롤백되지 않는다
        loginHistoryRecorder.failure(userId, email, method, reason, client)
        throw BusinessException(error)
    }

    private fun issueTokens(user: User, client: ClientInfo): AuthTokens {
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
        return AuthTokens(
            accessToken = jwtTokenProvider.issueAccessToken(user.id!!, user.email, user.nickname),
            refreshToken = rawRefresh,
            expiresIn = jwtTokenProvider.accessTokenTtlSeconds,
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
