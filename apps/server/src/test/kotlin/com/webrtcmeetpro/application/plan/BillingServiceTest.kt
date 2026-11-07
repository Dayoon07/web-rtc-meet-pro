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
    private val billing = BillingService(
        tossClient, toss, cipher, planService, fakes.billingKeyRepo, fakes.paymentRepo, fakes.subscriptionRepo, users,
        notifications, MailService(object : MailSender { override fun send(message: MailMessage) { mails += message } }),
        WebProperties("http://web.test"), noTx,
    )

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
        val e = assertThrows<BusinessException> { billing.purchase(uid, "TEAM", BillingCycle.MONTHLY, autoRenew = true) }
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
        billing.purchase(uid, "PRO", BillingCycle.YEARLY, autoRenew = false)
        val view = billing.purchase(uid, "TEAM", BillingCycle.MONTHLY, autoRenew = false)
        assertEquals("TEAM", view.plan.code)
        assertEquals(listOf(52_920L, 19_900L), charged)
        assertEquals(2, view.subscriptions.size)
        assertTrue(view.subscriptions.single { it.plan.code == "TEAM" }.inUse)

        // 한 달 조금 넘게 지나면 Team 은 끝났고 Pro(1년)가 남는다
        val later = LocalDateTime.now().plusMonths(1).plusDays(1)
        assertEquals("PRO", planService.currentPlanOf(uid, later).code)
        billing.processRenewals(later)
        assertEquals(SubscriptionStatus.EXPIRED, fakes.subscriptions.single { it.planId == team.id }.status)
        val after = planService.mySubscription(uid, later)
        assertEquals("PRO", after.plan.code)
        assertEquals("TEAM", after.expiryNotice?.plan?.code)
        assertTrue(sent.any { it.type == NotificationType.PLAN_EXPIRED })

        // 창을 닫으면 다시 안 보인다
        planService.acknowledgeExpiry(uid, after.expiryNotice!!.subscriptionId)
        assertNull(planService.mySubscription(uid, later).expiryNotice)

        // 1년 넘게 지나면 무료
        assertEquals("FREE", planService.currentPlanOf(uid, LocalDateTime.now().plusYears(1).plusDays(1)).code)
    }

    @Test
    fun `같은 플랜을 다시 사면 남은 기간 뒤로 이어 붙는다`() {
        registerCard()
        val first = billing.purchase(uid, "TEAM", BillingCycle.MONTHLY, autoRenew = false).currentPeriodEnd!!
        val second = billing.purchase(uid, "TEAM", BillingCycle.YEARLY, autoRenew = true)
        assertEquals(1, second.subscriptions.size)
        assertEquals(first.plusYears(1).withNano(0), second.currentPeriodEnd!!.withNano(0))
        assertTrue(second.subscriptions.single().autoRenew)
        assertEquals(BillingCycle.YEARLY, second.subscriptions.single().billingCycle)
    }

    @Test
    fun `자동결제가 켜져 있으면 기간이 끝날 때 다시 결제한다`() {
        registerCard()
        val end = billing.purchase(uid, "TEAM", BillingCycle.MONTHLY, autoRenew = true).currentPeriodEnd!!
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
        val end = billing.purchase(uid, "TEAM", BillingCycle.MONTHLY, autoRenew = true).currentPeriodEnd!!
        var now = end.plusMinutes(1)
        repeat(3) { i ->
            failNext = TossException("REJECT_CARD_COMPANY", "카드 한도 초과")
            billing.processRenewals(now)
            val sub = fakes.subscriptions.single { it.planId == team.id }
            if (i < 2) {
                assertEquals(SubscriptionStatus.PAST_DUE, sub.status)
                assertEquals("TEAM", planService.currentPlanOf(uid, now).code)
                // 다시 시도할 시각 전에는 결제하지 않는다
                billing.processRenewals(now.plusHours(1))
            } else {
                assertEquals(SubscriptionStatus.EXPIRED, sub.status)
            }
            now = now.plusDays(1).plusMinutes(1)
        }
        assertEquals("FREE", planService.currentPlanOf(uid, now).code)
        assertEquals(2, sent.count { it.type == NotificationType.PAYMENT_FAILED })
        assertEquals(1, sent.count { it.type == NotificationType.PLAN_EXPIRED })
        assertEquals(3, fakes.payments.count { it.status == PaymentStatus.FAILED })
        assertTrue(mails.any { it.subject.contains("자동결제에 실패") })
    }

    @Test
    fun `자동결제가 꺼져 있으면 3일 전에 한 번 알리고 끝나면 만료`() {
        registerCard()
        val end = billing.purchase(uid, "PRO", BillingCycle.MONTHLY, autoRenew = false).currentPeriodEnd!!
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
        val view = billing.purchase(uid, "TEAM", BillingCycle.MONTHLY, autoRenew = true)
        billing.removeCard(uid)
        val sub = planService.mySubscription(uid)
        assertNull(sub.card)
        assertFalse(sub.subscriptions.single().autoRenew)
        val e = assertThrows<BusinessException> { planService.setAutoRenew(uid, view.subscriptions.single().id, true) }
        assertEquals(ErrorCode.BILLING_CARD_REQUIRED, e.errorCode)
    }

    @Test
    fun `결제가 거절되면 실패 기록이 남고 구독은 생기지 않는다`() {
        registerCard()
        failNext = TossException("REJECT_CARD_COMPANY", "카드 한도 초과")
        val e = assertThrows<BusinessException> { billing.purchase(uid, "TEAM", BillingCycle.MONTHLY, autoRenew = true) }
        assertEquals(ErrorCode.PAYMENT_FAILED, e.errorCode)
        assertEquals("카드 한도 초과", e.params["reason"])
        assertEquals(PaymentStatus.FAILED, fakes.payments.single().status)
        assertEquals("FREE", planService.currentPlanOf(uid).code)
        assertNotNull(billing.history(uid).single().failureMessage)
    }

    @Test
    fun `무료 플랜은 결제할 수 없다`() {
        registerCard()
        assertEquals(ErrorCode.PLAN_NOT_PURCHASABLE, assertThrows<BusinessException> { billing.purchase(uid, "FREE", BillingCycle.MONTHLY, false) }.errorCode)
    }

    @Test
    fun `토스 키가 없으면 결제 기능이 꺼져 있다`() {
        val off = BillingService(
            tossClient, TossProperties(), cipher, planService, fakes.billingKeyRepo, fakes.paymentRepo, fakes.subscriptionRepo, users,
            notifications, MailService(object : MailSender { override fun send(message: MailMessage) {} }), WebProperties("http://web.test"), noTx,
        )
        assertEquals(ErrorCode.PAYMENT_NOT_CONFIGURED, assertThrows<BusinessException> { off.prepareCard(uid) }.errorCode)
        assertFalse(fakes.planService().mySubscription(uid).paymentsEnabled)
    }

    @Suppress("unused")
    private fun card(): BillingKey = fakes.billingKeys.single { it.active }
}
