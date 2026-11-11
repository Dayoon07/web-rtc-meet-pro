package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.application.mail.MailMessage
import com.webrtcmeetpro.application.mail.MailSender
import com.webrtcmeetpro.application.mail.MailService
import com.webrtcmeetpro.application.notification.NotificationService
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.properties.JwtProperties
import com.webrtcmeetpro.config.properties.TossProperties
import com.webrtcmeetpro.config.properties.WebProperties
import com.webrtcmeetpro.domain.notification.Notification
import com.webrtcmeetpro.domain.notification.NotificationRepository
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.BillingKey
import com.webrtcmeetpro.domain.plan.PaymentKind
import com.webrtcmeetpro.domain.plan.PaymentStatus
import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.domain.user.UserRepository
import com.webrtcmeetpro.external.toss.TossBilling
import com.webrtcmeetpro.external.toss.TossException
import com.webrtcmeetpro.external.toss.TossPayment
import com.webrtcmeetpro.external.toss.TossPaymentsClient
import com.webrtcmeetpro.infrastructure.realtime.RealtimePublisher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 요금제 적용 규칙과 토스 자동결제 흐름 (DB·토스 없이 메모리 구현으로) */
class BillingServiceTest {

    private val free = testPlan("FREE", 0, 0)
    private val pro = testPlan("PRO", 4900, 1, participants = 15)
    private val team = testPlan("TEAM", 19900, 2, recording = true, storageGb = 50, participants = 30)
    private val fakes = PlanFakes(listOf(free, pro, team))
    private val toss = TossProperties(clientKey = "test_ck_x", secretKey = "test_sk_x")
    private val planService = fakes.planService(toss)
    private val cipher = BillingKeyCipher(toss, JwtProperties("test-secret-0123456789abcdef0123456789", "t", Duration.ofMinutes(5), Duration.ofDays(1)))

    private val user = TestIds.assign(User.registerSocial("buyer@test.com", "구매자", null))
    private val users = object : UserRepository {
        override fun save(user: User) = user
        override fun findUserById(id: Long) = user.takeIf { it.id == id }
        override fun findByEmail(email: String) = null
        override fun existsByEmail(email: String) = false
        override fun findAllByIdIn(ids: Collection<Long>) = listOf(user).filter { it.id in ids }
    }
    private val uid get() = user.id!!
    /** 결제하는 사람이 만든 워크스페이스 */
    private val ws by lazy { fakes.team(uid, "우리 회사").id!! }

    private val sent = mutableListOf<Notification>()
    private val notifications = NotificationService(
        object : NotificationRepository {
            override fun save(notification: Notification) = TestIds.assign(notification).also { sent += it }
            override fun findNotificationById(id: Long) = null
            override fun findTop30ByUserIdOrderByIdDesc(userId: Long) = emptyList<Notification>()
            override fun findAllByUserIdAndReadAtIsNull(userId: Long) = emptyList<Notification>()
            override fun countByUserIdAndReadAtIsNull(userId: Long) = 0L
        },
        RealtimePublisher { },
        JsonMapper.builder().build(),
    )
    private val mails = mutableListOf<MailMessage>()

    /** 토스 대신: failNext 가 있으면 그 오류로 거절 */
    private var failNext: TossException? = null
    private val charged = mutableListOf<Long>()
    private val tossClient = object : TossPaymentsClient(toss, JsonMapper.builder().build()) {
        override fun issueBillingKey(authKey: String, customerKey: String) = TossBilling("bk_$authKey", "신한", "4330****1234")
        override fun charge(billingKey: String, customerKey: String, amount: Long, orderId: String, orderName: String, email: String?, name: String?): TossPayment {
            failNext?.let { failNext = null; throw it }
            charged += amount
            return TossPayment("pk_$orderId", "카드", "https://receipt.test/$orderId")
        }
    }
    private val noTx = object : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) {}
        override fun rollback(status: TransactionStatus) {}
    }
    private val mailService = MailService(object : MailSender { override fun send(message: MailMessage) { mails += message } })
    private val overage = MemberOverageService(
        planService, fakes.teamRepo, fakes.memberRepo, fakes.billingKeyRepo, users, notifications, mailService, WebProperties("http://web.test"),
    )
    private val billing = BillingService(
        tossClient, toss, cipher, planService, fakes.billingKeyRepo, fakes.paymentRepo, fakes.subscriptionRepo, users,
        fakes.teamRepo, notifications, mailService, WebProperties("http://web.test"), overage, noTx,
    )

    /** 워크스페이스에 멤버를 n 명 더 (소유자 포함 1 + n 명) */
    private fun addMembers(teamId: Long, n: Int) = repeat(n) { fakes.join(teamId, 10_000L + it) }

    private fun registerCard() {
        val prepared = billing.prepareCard(uid)
        billing.registerCard(uid, prepared.customerKey, "auth1")
    }

    @Test
    fun `연 결제는 월 가격 x 12 에서 10퍼센트 할인`() {
        assertEquals(214_920, team.price(BillingCycle.YEARLY))
        assertEquals(52_920, pro.price(BillingCycle.YEARLY))
        assertEquals(19_900, team.price(BillingCycle.MONTHLY))
    }

    @Test
    fun `카드 없이 결제하면 카드 등록부터 하라고 한다`() {
        val e = assertThrows<BusinessException> { billing.purchase(uid, ws, "TEAM", BillingCycle.MONTHLY, autoRenew = true) }
        assertEquals(ErrorCode.BILLING_CARD_REQUIRED, e.errorCode)
    }

    @Test
    fun `다른 사람이 만든 customerKey 로는 카드를 등록할 수 없다`() {
        val other = cipher.newCustomerKey(uid + 100)
        val e = assertThrows<BusinessException> { billing.registerCard(uid, other, "auth1") }
        assertEquals(ErrorCode.BILLING_CARD_INVALID, e.errorCode)
        // 빌링키는 암호화해서 저장한다
        registerCard()
        val saved = fakes.billingKeys.single { it.active }
        assertFalse(saved.billingKey.contains("bk_auth1"))
        assertEquals("bk_auth1", cipher.decrypt(saved.billingKey))
    }

    @Test
    fun `여러 플랜을 사면 가장 높은 플랜을 쓰고 끝나면 남은 낮은 플랜으로 돌아간다`() {
        registerCard()
        billing.purchase(uid, ws, "PRO", BillingCycle.YEARLY, autoRenew = false)
        val view = billing.purchase(uid, ws, "TEAM", BillingCycle.MONTHLY, autoRenew = false)
        assertEquals("TEAM", view.plan.code)
        assertEquals(listOf(52_920L, 19_900L), charged)
        assertEquals(2, view.subscriptions.size)
        assertTrue(view.subscriptions.single { it.plan.code == "TEAM" }.inUse)

        // 한 달 조금 넘게 지나면 Team 은 끝났고 Pro(1년)가 남는다
        val later = LocalDateTime.now().plusMonths(1).plusDays(1)
        assertEquals("PRO", planService.planOfTeam(ws, later).code)
        billing.processRenewals(later)
        assertEquals(SubscriptionStatus.EXPIRED, fakes.subscriptions.single { it.planId == team.id }.status)
        val after = planService.workspaceSubscription(ws, uid, later)
        assertEquals("PRO", after.plan.code)
        val notice = planService.expiryNotices(uid, later).single()
        assertEquals("TEAM", notice.plan.code)
        assertEquals("우리 회사", notice.teamName)
        assertEquals("PRO", notice.currentPlan.code)
        assertTrue(sent.any { it.type == NotificationType.PLAN_EXPIRED })

        // 창을 닫으면 다시 안 보인다
        planService.acknowledgeExpiry(uid, notice.subscriptionId)
        assertTrue(planService.expiryNotices(uid, later).isEmpty())

        // 1년 넘게 지나면 무료
        assertEquals("FREE", planService.planOfTeam(ws, LocalDateTime.now().plusYears(1).plusDays(1)).code)
    }

    @Test
    fun `같은 플랜을 다시 사면 남은 기간 뒤로 이어 붙는다`() {
        registerCard()
        val first = billing.purchase(uid, ws, "TEAM", BillingCycle.MONTHLY, autoRenew = false).currentPeriodEnd!!
        val second = billing.purchase(uid, ws, "TEAM", BillingCycle.YEARLY, autoRenew = true)
        assertEquals(1, second.subscriptions.size)
        assertEquals(first.plusYears(1).withNano(0), second.currentPeriodEnd!!.withNano(0))
        assertTrue(second.subscriptions.single().autoRenew)
        assertEquals(BillingCycle.YEARLY, second.subscriptions.single().billingCycle)
    }

    @Test
    fun `자동결제가 켜져 있으면 기간이 끝날 때 다시 결제한다`() {
        registerCard()
        val end = billing.purchase(uid, ws, "TEAM", BillingCycle.MONTHLY, autoRenew = true).currentPeriodEnd!!
        billing.processRenewals(end.plusMinutes(1))
        val sub = fakes.subscriptions.single { it.planId == team.id }
        assertEquals(SubscriptionStatus.ACTIVE, sub.status)
        assertTrue(sub.currentPeriodEnd!!.isAfter(end.plusDays(27)))
        assertEquals(listOf(19_900L, 19_900L), charged)
        assertEquals(2, fakes.payments.count { it.status == PaymentStatus.DONE })
    }

    @Test
    fun `자동결제가 3번 실패하면 만료되고 그동안은 플랜을 계속 쓴다`() {
        registerCard()
        val end = billing.purchase(uid, ws, "TEAM", BillingCycle.MONTHLY, autoRenew = true).currentPeriodEnd!!
        var now = end.plusMinutes(1)
        repeat(3) { i ->
            failNext = TossException("REJECT_CARD_COMPANY", "카드 한도 초과")
            billing.processRenewals(now)
            val sub = fakes.subscriptions.single { it.planId == team.id }
            if (i < 2) {
                assertEquals(SubscriptionStatus.PAST_DUE, sub.status)
                assertEquals("TEAM", planService.planOfTeam(ws, now).code)
                // 다시 시도할 시각 전에는 결제하지 않는다
                billing.processRenewals(now.plusHours(1))
            } else {
                assertEquals(SubscriptionStatus.EXPIRED, sub.status)
            }
            now = now.plusDays(1).plusMinutes(1)
        }
        assertEquals("FREE", planService.planOfTeam(ws, now).code)
        assertEquals(2, sent.count { it.type == NotificationType.PAYMENT_FAILED })
        assertEquals(1, sent.count { it.type == NotificationType.PLAN_EXPIRED })
        assertEquals(3, fakes.payments.count { it.status == PaymentStatus.FAILED })
        assertTrue(mails.any { it.subject.contains("자동결제에 실패") })
    }

    @Test
    fun `자동결제가 꺼져 있으면 3일 전에 한 번 알리고 끝나면 만료`() {
        registerCard()
        val end = billing.purchase(uid, ws, "PRO", BillingCycle.MONTHLY, autoRenew = false).currentPeriodEnd!!
        billing.processRenewals(end.minusDays(2))
        billing.processRenewals(end.minusDays(1))
        assertEquals(1, sent.count { it.type == NotificationType.PLAN_EXPIRING })
        billing.processRenewals(end.plusMinutes(1))
        assertEquals(SubscriptionStatus.EXPIRED, fakes.subscriptions.single { it.planId == pro.id }.status)
        assertEquals(listOf(4_900L), charged)
    }

    @Test
    fun `카드를 지우면 자동결제가 꺼지고 다시 켜려면 카드가 필요하다`() {
        registerCard()
        val view = billing.purchase(uid, ws, "TEAM", BillingCycle.MONTHLY, autoRenew = true)
        billing.removeCard(uid)
        val sub = planService.workspaceSubscription(ws, uid)
        assertNull(sub.card)
        assertFalse(sub.subscriptions.single().autoRenew)
        val e = assertThrows<BusinessException> { planService.setAutoRenew(uid, view.subscriptions.single().id, true) }
        assertEquals(ErrorCode.BILLING_CARD_REQUIRED, e.errorCode)
    }

    @Test
    fun `결제가 거절되면 실패 기록이 남고 구독은 생기지 않는다`() {
        registerCard()
        failNext = TossException("REJECT_CARD_COMPANY", "카드 한도 초과")
        val e = assertThrows<BusinessException> { billing.purchase(uid, ws, "TEAM", BillingCycle.MONTHLY, autoRenew = true) }
        assertEquals(ErrorCode.PAYMENT_FAILED, e.errorCode)
        assertEquals("카드 한도 초과", e.params["reason"])
        assertEquals(PaymentStatus.FAILED, fakes.payments.single().status)
        assertEquals("FREE", planService.planOfTeam(ws).code)
        assertNotNull(billing.history(uid).single().failureMessage)
    }

    @Test
    fun `요금제는 워크스페이스마다 - 다른 워크스페이스는 무료 그대로`() {
        registerCard()
        val other = fakes.team(uid, "취미 모임").id!!
        billing.purchase(uid, ws, "TEAM", BillingCycle.MONTHLY, autoRenew = false)
        assertEquals("TEAM", planService.planOfTeam(ws).code)
        assertEquals("FREE", planService.planOfTeam(other).code)
        assertEquals("TEAM", planService.bestOwnedPlan(uid).code)
    }

    @Test
    fun `워크스페이스 소유자만 결제할 수 있고 멤버는 요금제를 볼 수만 있다`() {
        registerCard()
        val memberId = uid + 500
        fakes.join(ws, memberId)
        assertEquals(ErrorCode.NOT_TEAM_OWNER, assertThrows<BusinessException> { billing.purchase(memberId, ws, "TEAM", BillingCycle.MONTHLY, false) }.errorCode)
        val view = planService.workspaceSubscription(ws, memberId)
        assertFalse(view.canManage)
        assertTrue(planService.workspaceSubscription(ws, uid).canManage)
    }

    @Test
    fun `영수증에는 공급가액과 부가세, 카드 정보, 워크스페이스 이름`() {
        registerCard()
        billing.purchase(uid, ws, "PRO", BillingCycle.MONTHLY, autoRenew = false)
        val p = billing.history(uid).single()
        assertEquals(4_900, p.amount)
        assertEquals(4_455, p.supplyAmount)
        assertEquals(445, p.vat)
        assertEquals("신한", p.cardCompany)
        assertEquals("우리 회사", p.teamName)
        assertTrue(p.orderName.contains("우리 회사"))
    }

    @Test
    fun `무료 플랜은 결제할 수 없다`() {
        registerCard()
        assertEquals(ErrorCode.PLAN_NOT_PURCHASABLE, assertThrows<BusinessException> { billing.purchase(uid, ws, "FREE", BillingCycle.MONTHLY, false) }.errorCode)
    }

    @Test
    fun `토스 키가 없으면 결제 기능이 꺼져 있다`() {
        val off = BillingService(
            tossClient, TossProperties(), cipher, planService, fakes.billingKeyRepo, fakes.paymentRepo, fakes.subscriptionRepo, users,
            fakes.teamRepo, notifications, MailService(object : MailSender { override fun send(message: MailMessage) {} }), WebProperties("http://web.test"), overage, noTx,
        )
        assertEquals(ErrorCode.PAYMENT_NOT_CONFIGURED, assertThrows<BusinessException> { off.prepareCard(uid) }.errorCode)
        assertFalse(fakes.planService().workspaceSubscription(ws, uid).paymentsEnabled)
    }

    @Suppress("unused")
    private fun card(): BillingKey = fakes.billingKeys.single { it.active }

    // ------------------------------------------------------------- 멤버 초과 요금

    @Test
    fun `한도 초과 허용은 소유자가 카드 등록하고 동의해야 켜진다`() {
        assertEquals(ErrorCode.BILLING_CARD_REQUIRED, assertThrows<BusinessException> { overage.setAllowed(ws, uid, allowed = true, agree = true) }.errorCode)
        registerCard()
        assertEquals(ErrorCode.OVERAGE_CONSENT_REQUIRED, assertThrows<BusinessException> { overage.setAllowed(ws, uid, allowed = true, agree = false) }.errorCode)
        val view = overage.setAllowed(ws, uid, allowed = true, agree = true)
        assertTrue(view.allowed)
        assertNotNull(fakes.teams.single { it.id == ws }.overageAgreedAt)
        assertEquals(9900, view.pricePerMember)
    }

    @Test
    fun `허용하지 않으면 한도에서 막히고, 허용하면 넘게 들어올 수 있다`() {
        val id = ws
        val t = fakes.teams.single { it.id == id }
        val e = assertThrows<BusinessException> { overage.requireSeat(t, free, 10) }
        assertEquals(ErrorCode.PLAN_MEMBER_LIMIT, e.errorCode)
        assertEquals(true, e.params["overage"])
        registerCard()
        overage.setAllowed(ws, uid, allowed = true, agree = true)
        overage.requireSeat(t, free, 10)
        assertEquals(ErrorCode.OVERAGE_MAX_MEMBERS, assertThrows<BusinessException> { overage.requireSeat(t, free, MemberOverageService.HARD_CAP.toLong()) }.errorCode)
    }

    @Test
    fun `무료 워크스페이스가 넘으면 경고 메일, 한 달 뒤 초과분만 따로 결제, 3일 전 안내`() {
        registerCard()
        overage.setAllowed(ws, uid, allowed = true, agree = true)
        val now = LocalDateTime.of(2026, 11, 11, 10, 0)
        addMembers(ws, 11) // 1 + 11 = 12명, 무료 한도 10명 → 2명 초과
        overage.onMemberJoined(ws, now)
        overage.onMemberJoined(ws, now.plusHours(1))
        val warn = mails.filter { "한도를 넘었어요" in it.subject }
        assertEquals(1, warn.size, "경고 메일은 하루에 한 번")
        assertTrue("9,900원 × 2명 = 19,800원" in warn.single().text, warn.single().text)
        val team = fakes.teams.single { it.id == ws }
        assertEquals(now.plusMonths(1), team.overageNextChargeAt)

        billing.processOverage(now.plusMonths(1).minusDays(2))
        billing.processOverage(now.plusMonths(1).minusDays(1))
        assertEquals(1, mails.count { "결제 예정 금액 안내" in it.subject }, "3일 전 안내는 한 번")
        assertTrue(charged.isEmpty())

        billing.processOverage(now.plusMonths(1).plusMinutes(1))
        assertEquals(listOf(19_800L), charged)
        val p = fakes.payments.single()
        assertEquals(PaymentKind.OVERAGE, p.paymentKind)
        assertEquals(2, p.overageMembers)
        assertEquals(PaymentStatus.DONE, p.status)
        assertEquals(now.plusMonths(2), team.overageNextChargeAt, "다음 달 같은 날")
    }

    @Test
    fun `결제일에 멤버가 한도 안이면 청구하지 않고 일정을 지운다`() {
        registerCard()
        overage.setAllowed(ws, uid, allowed = true, agree = true)
        val now = LocalDateTime.of(2026, 11, 11, 10, 0)
        addMembers(ws, 11)
        overage.onMemberJoined(ws, now)
        fakes.members.filter { it.teamId == ws && it.userId >= 10_000L }.take(2).forEach { it.leave() }
        billing.processOverage(now.plusMonths(1).plusMinutes(1))
        assertTrue(charged.isEmpty())
        assertNull(fakes.teams.single { it.id == ws }.overageNextChargeAt)
    }

    @Test
    fun `월 자동결제 플랜은 갱신 때 플랜 요금 + 초과 요금을 한 번에, 3일 전에 금액 안내`() {
        registerCard()
        billing.purchase(uid, ws, "PRO", BillingCycle.MONTHLY, autoRenew = true) // PRO 한도 20명
        overage.setAllowed(ws, uid, allowed = true, agree = true)
        addMembers(ws, 22) // 23명 → 3명 초과
        overage.onMemberJoined(ws)
        assertNull(fakes.teams.single { it.id == ws }.overageNextChargeAt, "따로 결제하지 않고 갱신 때 같이")

        val end = fakes.subscriptions.single { it.planId == pro.id && it.teamId == ws }.currentPeriodEnd!!
        billing.processRenewals(end.minusDays(2))
        val notice = mails.single { "결제 예정 금액 안내" in it.subject }
        assertTrue("합계 34,600원" in notice.text, notice.text) // 4,900 + 3 × 9,900

        billing.processRenewals(end.plusMinutes(1))
        assertEquals(listOf(4_900L, 34_600L), charged)
        val renewal = fakes.payments.last()
        assertEquals(PaymentKind.PLAN, renewal.paymentKind)
        assertEquals(3, renewal.overageMembers)
        assertEquals(29_700, renewal.overageAmount)
        assertTrue("초과 3명" in renewal.orderName)
        // 영수증(결제 내역)에 초과분이 따로
        val view = billing.history(uid).first { it.orderId == renewal.orderId }
        assertEquals(29_700, view.overageAmount)
    }

    @Test
    fun `초과한 멤버가 있는 동안은 끌 수 없고, 정리하면 끌 수 있다`() {
        registerCard()
        overage.setAllowed(ws, uid, allowed = true, agree = true)
        addMembers(ws, 11)
        val e = assertThrows<BusinessException> { overage.setAllowed(ws, uid, allowed = false, agree = false) }
        assertEquals(ErrorCode.OVERAGE_IN_USE, e.errorCode)
        assertEquals(2L, e.params["count"])
        fakes.members.filter { it.teamId == ws && it.userId >= 10_000L }.take(2).forEach { it.leave() }
        assertFalse(overage.setAllowed(ws, uid, allowed = false, agree = false).allowed)
    }

    @Test
    fun `초과 요금 결제가 3번 실패하면 한도 초과 허용이 꺼진다`() {
        registerCard()
        overage.setAllowed(ws, uid, allowed = true, agree = true)
        val now = LocalDateTime.of(2026, 11, 11, 10, 0)
        addMembers(ws, 11)
        overage.onMemberJoined(ws, now)
        var at = now.plusMonths(1).plusMinutes(1)
        repeat(3) {
            failNext = TossException("REJECT_CARD_COMPANY", "한도 초과", uncertain = false)
            billing.processOverage(at)
            at = at.plusDays(1).plusMinutes(1)
        }
        val team = fakes.teams.single { it.id == ws }
        assertFalse(team.overageAllowed)
        assertEquals(3, fakes.payments.count { it.status == PaymentStatus.FAILED })
        assertTrue(sent.any { it.type == NotificationType.PAYMENT_FAILED })
    }
}
