package com.webrtcmeetpro.application.auth

import com.webrtcmeetpro.domain.auth.LoginHistory
import com.webrtcmeetpro.domain.auth.LoginHistoryRepository
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
import com.webrtcmeetpro.application.mail.MailMessage
import com.webrtcmeetpro.application.mail.MailSender
import com.webrtcmeetpro.application.mail.MailService
import com.webrtcmeetpro.config.properties.WebProperties
import java.net.URLDecoder
import com.webrtcmeetpro.application.plan.PlanService
import com.webrtcmeetpro.domain.plan.Payment
import com.webrtcmeetpro.domain.plan.PaymentRepository
import com.webrtcmeetpro.domain.plan.Plan
import com.webrtcmeetpro.domain.plan.PlanRepository
import com.webrtcmeetpro.domain.plan.Subscription
import com.webrtcmeetpro.domain.plan.SubscriptionRepository
import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import com.webrtcmeetpro.domain.team.Team
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamStatus
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.domain.user.UserProfile
import com.webrtcmeetpro.domain.user.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DDD 리포지토리 포트를 메모리 구현으로 바꿔 구글 로그인 규칙만 검증 (DB / Google 없이) */
class GoogleLoginTest {

    private var seq = 0L
    private fun <T : Any> assignId(entity: T): T {
        var c: Class<*>? = entity.javaClass
        while (c != null) {
            c.declaredFields.firstOrNull { it.name == "id" }?.let {
                it.isAccessible = true
                if (it.get(entity) == null) it.set(entity, ++seq)
                return entity
            }
            c = c.superclass
        }
        return entity
    }

    private val users = mutableListOf<User>()
    private val socials = mutableListOf<SocialAccount>()
    private val histories = mutableListOf<LoginHistory>()
    private val subscriptions = mutableListOf<Subscription>()
    private val freePlan = assignId(
        Plan("FREE", "무료", null, 0, 1, 10, 1, recordingEnabled = false),
    )

    private val userRepo = object : UserRepository {
        override fun save(user: User) = assignId(user).also { if (it !in users) users += it }
        override fun findUserById(id: Long) = users.find { it.id == id }
        override fun findByEmail(email: String) = users.find { it.email == email }
        override fun existsByEmail(email: String) = users.any { it.email == email }
        override fun findAllByIdIn(ids: Collection<Long>) = users.filter { it.id in ids }
    }
    private val socialRepo = object : SocialAccountRepository {
        override fun save(account: SocialAccount) = assignId(account).also { if (it !in socials) socials += it }
        override fun findByProviderAndProviderUserId(provider: SocialProvider, providerUserId: String) =
            socials.find { it.provider == provider && it.providerUserId == providerUserId }
        override fun findAllByUserId(userId: Long) = socials.filter { it.userId == userId }
        override fun delete(account: SocialAccount) { socials -= account }
    }
    private val refreshTokens = mutableListOf<RefreshToken>()
    private val refreshRepo = object : RefreshTokenRepository {
        override fun save(token: RefreshToken) = assignId(token).also { if (it !in refreshTokens) refreshTokens += it }
        override fun findByTokenHash(tokenHash: String): RefreshToken? = refreshTokens.find { it.tokenHash == tokenHash }
        override fun findAllByUserIdAndRevokedAtIsNull(userId: Long) = refreshTokens.filter { it.userId == userId && it.revokedAt == null }
    }
    private val historyRepo = object : LoginHistoryRepository {
        override fun save(history: LoginHistory) = assignId(history).also { histories += it }
    }
    private val planService = PlanService(
        object : PlanRepository {
            override fun findPlanById(id: Long) = freePlan.takeIf { it.id == id }
            override fun findByCode(code: String) = freePlan.takeIf { it.code == code }
            override fun findAllByActiveTrueOrderBySortOrderAsc() = listOf(freePlan)
        },
        object : SubscriptionRepository {
            override fun save(subscription: Subscription) = assignId(subscription).also { if (it !in subscriptions) subscriptions += it }
            override fun flush() {}
            override fun findByUserIdAndStatus(userId: Long, status: SubscriptionStatus) =
                subscriptions.find { it.userId == userId && it.status == status }
            override fun findAllByUserIdOrderByStartedAtDesc(userId: Long) = subscriptions.filter { it.userId == userId }
        },
        object : PaymentRepository {
            override fun save(payment: Payment) = assignId(payment)
            override fun findByOrderId(orderId: String): Payment? = null
            override fun findAllByUserIdOrderByCreatedDtDesc(userId: Long) = emptyList<Payment>()
        },
        object : TeamRepository {
            override fun save(team: Team) = team
            override fun findTeamById(id: Long): Team? = null
            override fun countByOwnerIdAndStatus(ownerId: Long, status: TeamStatus) = 0L
            override fun findAllByIdInAndStatus(ids: Collection<Long>, status: TeamStatus) = emptyList<Team>()
        },
    )

    private val encoder = BCryptPasswordEncoder(4)
    private val jwtProps = JwtProperties("test-secret-0123456789abcdef0123456789", "test", Duration.ofMinutes(5), Duration.ofDays(1))
    private val mails = mutableListOf<MailMessage>()
    private val verifyTokens = EmailVerificationTokens(jwtProps)
    private val emailVerification = EmailVerificationService(
        userRepo, verifyTokens, MailService(object : MailSender { override fun send(message: MailMessage) { mails += message } }), WebProperties("http://web.test"),
    )
    private val service = AuthService(
        userRepo, refreshRepo, socialRepo, encoder, JwtTokenProvider(jwtProps), jwtProps, planService, LoginHistoryRecorder(historyRepo), emailVerification,
    )
    private val client = ClientInfo("127.0.0.1", "test")

    private fun google(sub: String = "g-1", email: String = "new@gmail.com", verified: Boolean = true) =
        GoogleUserInfo(sub, email, verified, "Kim Google", "https://lh3.googleusercontent.com/p.jpg")

    @Test
    fun `처음 보는 구글 계정은 비밀번호 없이 가입되고 FREE 구독과 연결이 생긴다`() {
        val userId = service.loginWithGoogle(google(), client)

        val user = users.single()
        assertEquals(userId, user.id)
        assertFalse(user.hasPassword)
        assertFalse(user.isProfileCompleted, "실명·전화번호 등은 온보딩에서 받아야 한다")
        assertEquals("https://lh3.googleusercontent.com/p.jpg", user.profileImageUrl)
        assertEquals("Kim Google", user.nickname)
        assertEquals(userId, socials.single().userId)
        assertEquals(freePlan.id, subscriptions.single { it.userId == userId }.planId)
        assertTrue(histories.single().let { it.success && it.loginMethod == LoginMethod.GOOGLE })
    }

    private val profile = UserProfile("민지", "김민지", "+821012345678", "KR", 1996, null)

    @Test
    fun `같은 이메일의 이메일 인증된 기존 계정이 있으면 새로 만들지 않고 연결한다`() {
        val existing = userRepo.save(User.register("kim@gmail.com", encoder.encode("abcd1234")!!, profile, false).apply { verifyEmail() })

        val userId = service.loginWithGoogle(google(email = "KIM@gmail.com"), client)

        assertEquals(existing.id, userId)
        assertEquals(1, users.size)
        assertTrue(existing.hasPassword, "기존 비밀번호 로그인은 그대로 유지")
        assertEquals(existing.id, socials.single().userId)
    }

    @Test
    fun `이메일 인증 전인 기존 계정에 구글로 연결하면 그 비밀번호를 지운다 (남이 먼저 가입했을 수 있다)`() {
        val existing = userRepo.save(User.register("kim@gmail.com", encoder.encode("abcd1234")!!, profile, false))

        service.loginWithGoogle(google(email = "kim@gmail.com"), client)

        assertFalse(existing.hasPassword, "이메일 주인이 아닌 사람이 만든 비밀번호일 수 있다")
        assertTrue(existing.isEmailVerified)
        val e = assertThrows<BusinessException> { service.login(LoginCommand("kim@gmail.com", "abcd1234"), client) }
        assertEquals(ErrorCode.SOCIAL_ACCOUNT_ONLY, e.errorCode)
    }

    @Test
    fun `구글로 새로 가입하면 이메일 인증된 상태다`() {
        service.loginWithGoogle(google(), client)
        assertTrue(users.single().isEmailVerified)
        assertTrue(mails.isEmpty(), "구글 가입자에게는 인증 메일을 보내지 않는다")
    }

    @Test
    fun `이메일로 가입하면 인증 메일이 가고, 링크의 토큰으로 인증된다`() {
        service.signUp(SignUpCommand("lee@test.com", "abcd1234", profile, true, true, false), client)
        val user = users.single()
        assertFalse(user.isEmailVerified)

        val mail = mails.single()
        assertEquals("lee@test.com", mail.to)
        val token = URLDecoder.decode(Regex("""token=(\S+)""").find(mail.text)!!.groupValues[1], Charsets.UTF_8)
        assertEquals(user.id, verifyTokens.userIdOf(token))

        val forged = token.dropLast(2) + "xx"
        assertEquals(ErrorCode.EMAIL_VERIFY_TOKEN_INVALID, assertThrows<BusinessException> { emailVerification.confirm(forged) }.errorCode)
        emailVerification.confirm(token)
        assertTrue(user.isEmailVerified)
    }

    @Test
    fun `인증 메일 다시 보내기는 60초에 한 번`() {
        service.signUp(SignUpCommand("lee@test.com", "abcd1234", profile, true, true, false), client)
        val e = assertThrows<BusinessException> { emailVerification.resend(users.single().id!!) }
        assertEquals(ErrorCode.RESEND_TOO_SOON, e.errorCode)
        assertEquals(1, mails.size)
    }

    @Test
    fun `비밀번호 바꾸기는 현재 비밀번호를 확인하고 다른 로그인을 끊는다`() {
        val first = service.signUp(SignUpCommand("lee@test.com", "abcd1234", profile, true, true, false), client)
        val id = users.single().id!!

        val wrong = assertThrows<BusinessException> { service.changePassword(id, "wrong-pass", "newpass99", client) }
        assertEquals(ErrorCode.INVALID_PASSWORD, wrong.errorCode)

        val fresh = service.changePassword(id, "abcd1234", "newpass99", client)
        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, assertThrows<BusinessException> { service.refresh(first.refreshToken, client) }.errorCode)
        service.refresh(fresh.refreshToken, client)
        service.login(LoginCommand("lee@test.com", "newpass99"), client)
    }

    @Test
    fun `구글 전용 계정은 현재 비밀번호 없이 비밀번호를 만들 수 있다`() {
        val id = service.loginWithGoogle(google(), client)
        service.changePassword(id, null, "newpass99", client)
        service.login(LoginCommand("new@gmail.com", "newpass99"), client)
    }

    @Test
    fun `이메일 미인증 구글 계정은 기존 계정에 연결하지 않고 거절한다`() {
        val e = assertThrows<BusinessException> { service.loginWithGoogle(google(verified = false), client) }

        assertEquals(ErrorCode.OAUTH_EMAIL_NOT_VERIFIED, e.errorCode)
        assertTrue(users.isEmpty())
        assertTrue(socials.isEmpty())
        assertTrue(histories.single().let { !it.success && it.loginMethod == LoginMethod.GOOGLE })
    }

    @Test
    fun `두 번째 로그인은 연결된 계정을 그대로 쓴다`() {
        val first = service.loginWithGoogle(google(), client)
        val second = service.loginWithGoogle(google(email = "changed@gmail.com"), client)

        assertEquals(first, second)
        assertEquals(1, users.size)
        assertEquals(1, socials.size)
        assertNotNull(socials.single().lastLoginAt)
    }

    @Test
    fun `구글 전용 계정으로 비밀번호 로그인을 시도하면 구글 로그인을 안내한다`() {
        service.loginWithGoogle(google(), client)

        val e = assertThrows<BusinessException> { service.login(LoginCommand("new@gmail.com", "whatever1"), client) }

        assertEquals(ErrorCode.SOCIAL_ACCOUNT_ONLY, e.errorCode)
        assertNull(users.single().passwordHash)
    }
}
