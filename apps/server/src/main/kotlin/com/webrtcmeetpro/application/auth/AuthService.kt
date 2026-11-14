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
    private val emailVerification: EmailVerificationService,
    /** 짧은 트랜잭션 (Spring Boot 의 TransactionTemplate). 비밀번호 해시는 이 밖에서 한다 */
    private val transactions: TransactionOperations,
) {
    private val random = SecureRandom()

    /**
     * 가입. 비밀번호 해시(bcrypt, CPU 를 많이 씀)는 트랜잭션 밖에서 만들고, 저장만 짧은 트랜잭션으로 한다
     * (부하 테스트: 해시하는 동안 DB 연결을 잡고 있으면 가입·로그인이 몰릴 때 DB 연결이 다 묶여 다른 요청까지 막혔다)
     */
    fun signUp(command: SignUpCommand, client: ClientInfo): AuthTokens {
        if (!command.termsAgreed || !command.privacyAgreed) throw BusinessException(ErrorCode.TERMS_NOT_AGREED)
        val email = command.email.trim().lowercase()
        requireNewEmail(email)
        val hash = passwordEncoder.encode(command.password)!!

        return transactions.execute {
            // 해시하는 사이에 같은 이메일로 가입했을 수 있어 한 번 더 확인
            requireNewEmail(email)
            val user = userRepository.save(User.register(email, hash, command.profile, command.marketingAgreed))
            planService.startFreeSubscription(user.id!!)
            // 이메일 주인인지 확인하는 메일 (커밋된 뒤 발송). 인증 전에는 받은 팀 초대를 볼 수 없다
            emailVerification.send(user)
            user.recordLogin()
            loginHistoryRecorder.success(user.id!!, email, LoginMethod.PASSWORD, client)
            issueTokens(user, client)
        }!!
    }

    /** 한 이메일 = 계정 하나. 이미 있으면 어떤 방법으로 로그인하면 되는지 알려 준다 */
    private fun requireNewEmail(email: String) {
        userRepository.findByEmail(email)?.let { existing ->
            throw BusinessException(if (existing.signInMethod == "GOOGLE") ErrorCode.EMAIL_REGISTERED_WITH_GOOGLE else ErrorCode.EMAIL_ALREADY_EXISTS)
        }
    }

    /**
     * 로그인한 상태에서 비밀번호 바꾸기 (구글로만 가입한 계정은 새로 만들기 — 현재 비밀번호 없이).
     * 다른 기기의 로그인은 모두 끊고, 지금 기기에는 새 토큰을 준다.
     */
    @Transactional
    fun changePassword(userId: Long, currentPassword: String?, newPassword: String, client: ClientInfo): AuthTokens {
        val user = userRepository.findUserById(userId)?.takeIf { it.isActive } ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val hash = user.passwordHash
        if (hash != null && (currentPassword == null || !passwordEncoder.matches(currentPassword, hash))) {
            throw BusinessException(ErrorCode.INVALID_PASSWORD)
        }
        user.changePassword(passwordEncoder.encode(newPassword)!!)
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId).forEach { it.revoke() }
        return issueTokens(user, client)
    }

    /** 회원가입 화면의 이메일 확인: 이미 있으면 그 이메일의 로그인 방법 (PASSWORD · GOOGLE), 없으면 null */
    @Transactional(readOnly = true)
    fun registeredSignInMethod(email: String): String? = userRepository.findByEmail(email.trim().lowercase())?.signInMethod

    /**
     * 이메일 로그인. 비밀번호 확인(bcrypt)은 DB 연결을 잡지 않은 채로 하고, 로그인 기록·토큰만 짧은 트랜잭션으로 한다
     * (부하 테스트: 1분에 1,000명이 로그인하자 DB 연결 10개가 bcrypt 동안 묶여 로그인 안 한 사람의 요청까지 30초씩 기다렸다)
     */
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

        return transactions.execute {
            val current = userRepository.findUserById(user.id!!)?.takeIf { it.isActive }
                ?: fail(user.id, email, LoginFailureReason.USER_NOT_ACTIVE, ErrorCode.USER_NOT_ACTIVE, client)
            current.recordLogin()
            loginHistoryRecorder.success(current.id!!, email, LoginMethod.PASSWORD, client)
            issueTokens(current, client)
        }!!
    }

    /**
     * 구글 로그인. 이 구글 계정이 연결된 계정 → 없으면 신규 가입.
     * 같은 이메일로 이미 이메일·비밀번호 가입한 계정이 있으면 자동으로 합치지 않고 거절한다 (이메일로 로그인하라고 안내).
     * 한 이메일 = 계정 하나라서, 어느 쪽으로 먼저 가입했든 그 방법으로만 로그인한다.
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
