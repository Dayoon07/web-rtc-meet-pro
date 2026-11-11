package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.application.mail.MailLanguage
import com.webrtcmeetpro.application.mail.MailService
import com.webrtcmeetpro.application.mail.MailTemplates
import com.webrtcmeetpro.application.mail.PlanMailKind
import com.webrtcmeetpro.application.notification.NotificationService
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.JacksonConfig
import com.webrtcmeetpro.config.properties.TossProperties
import com.webrtcmeetpro.config.properties.WebProperties
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.BillingKey
import com.webrtcmeetpro.domain.plan.BillingKeyRepository
import com.webrtcmeetpro.domain.plan.Payment
import com.webrtcmeetpro.domain.plan.PaymentRepository
import com.webrtcmeetpro.domain.plan.PaymentStatus
import com.webrtcmeetpro.domain.plan.Plan
import com.webrtcmeetpro.domain.plan.Subscription
import com.webrtcmeetpro.domain.plan.SubscriptionRepository
import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import com.webrtcmeetpro.domain.user.UserRepository
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamStatus
import com.webrtcmeetpro.external.toss.TossException
import com.webrtcmeetpro.external.toss.TossPaymentsClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

/** 카드 등록창을 열 때 필요한 값 (웹이 토스 SDK 에 넘긴다) */
data class CardRegistrationView(val clientKey: String, val customerKey: String, val customerEmail: String, val customerName: String)

/** 결제 내역 한 줄 + 영수증 창에 보여 줄 값 */
data class PaymentView(
    val orderId: String,
    val orderName: String,
    val planCode: String,
    val planName: String,
    val amount: Long,
    /** 공급가액 = 금액 × 10/11 (원 단위 반올림), 부가세 = 금액 - 공급가액 */
    val supplyAmount: Long,
    val vat: Long,
    val status: PaymentStatus,
    val billingCycle: BillingCycle?,
    val method: String?,
    val cardCompany: String?,
    val cardNumberMasked: String?,
    val approveNo: String?,
    val receiptUrl: String?,
    val failureMessage: String?,
    val approvedAt: LocalDateTime?,
    val periodEnd: LocalDateTime?,
    val createdAt: LocalDateTime,
    val teamId: Long?,
    val teamName: String?,
)

/**
 * 토스페이먼츠 자동결제(빌링).
 * 1) 카드 등록: 웹이 토스 카드 등록창을 띄우고, 돌아온 authKey 로 빌링키를 받아 암호화해 저장한다.
 * 2) 결제: 등록한 카드(빌링키)로 바로 결제하고, 성공하면 구독 기간을 늘린다 (월 = 월 가격, 연 = 월 가격 × 12 × 0.9).
 * 3) 갱신(스케줄러): 기간이 끝난 구독은 자동결제면 다시 결제하고, 아니면 만료. 실패하면 하루 간격으로 3번까지 다시 시도.
 *
 * 외부 결제 호출은 DB 트랜잭션 밖에서 한다 (결제 기록이 롤백돼 사라지지 않게, 오래 걸리는 호출이 DB 를 붙잡지 않게).
 */
@Service
class BillingService(
    private val toss: TossPaymentsClient,
    private val tossProperties: TossProperties,
    private val cipher: BillingKeyCipher,
    private val planService: PlanService,
    private val billingKeys: BillingKeyRepository,
    private val payments: PaymentRepository,
    private val subscriptions: SubscriptionRepository,
    private val users: UserRepository,
    private val teams: TeamRepository,
    private val notifications: NotificationService,
    private val mail: MailService,
    private val web: WebProperties,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    // ----------------------------------------------------------------- 카드

    fun prepareCard(userId: Long): CardRegistrationView {
        requireEnabled()
        val user = users.findUserById(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        return CardRegistrationView(tossProperties.clientKey, cipher.newCustomerKey(userId), user.email, user.realName ?: user.nickname)
    }

    /** 카드 등록창에서 돌아옴 → 빌링키 발급. 전에 등록한 카드는 해지 (한 사람당 카드 하나) */
    fun registerCard(userId: Long, customerKey: String, authKey: String): CardView {
        requireEnabled()
        if (!cipher.isOwnCustomerKey(userId, customerKey)) throw BusinessException(ErrorCode.BILLING_CARD_INVALID)
        val issued = try {
            toss.issueBillingKey(authKey, customerKey)
        } catch (e: TossException) {
            throw BusinessException(ErrorCode.BILLING_CARD_FAILED, mapOf("reason" to e.message), e.message)
        }
        return tx.execute {
            billingKeys.findAllByUserIdAndActiveTrue(userId).forEach { it.revoke() }
            val saved = billingKeys.save(
                BillingKey(userId, customerKey, cipher.encrypt(issued.billingKey), issued.cardCompany?.take(30), issued.cardNumber?.take(30)),
            )
            planService.cardView(saved)
        }!!.also { log.info("카드 등록 (userId={}, {} {})", userId, it.company, it.numberMasked) }
    }

    /** 카드 삭제. 자동결제할 카드가 없어지므로 모든 구독의 자동결제도 끈다 (기간 끝까지는 계속 쓸 수 있다) */
    fun removeCard(userId: Long) {
        tx.executeWithoutResult {
            billingKeys.findAllByUserIdAndActiveTrue(userId).forEach { it.revoke() }
            subscriptions.findAllByUserIdAndStatusIn(userId, listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE))
                .forEach { it.autoRenew = false }
        }
    }

    // ----------------------------------------------------------------- 결제

    /**
     * 워크스페이스 플랜 결제 (소유자만). 같은 플랜을 이미 쓰고 있으면 남은 기간 뒤로 늘어나고, 다른 플랜이면 따로 새 구독이 생긴다
     * (가장 높은 플랜이 적용되고, 그게 끝나면 남은 낮은 플랜으로).
     */
    fun purchase(userId: Long, teamId: Long, planCode: String, cycle: BillingCycle, autoRenew: Boolean): SubscriptionView {
        requireEnabled()
        val team = planService.requireOwnedTeam(teamId, userId)
        val plan = planService.findPlan(planCode)
        if (plan.isFree) throw BusinessException(ErrorCode.PLAN_NOT_PURCHASABLE)
        val card = billingKeys.findByUserIdAndActiveTrue(userId) ?: throw BusinessException(ErrorCode.BILLING_CARD_REQUIRED)

        val payment = tx.execute { payments.save(newPayment(userId, teamId, team.name, plan, cycle, card)) }!!
        try {
            chargeAndApply(payment.orderId, card) { now -> planService.applyPurchase(teamId, userId, plan, cycle, autoRenew, now) }
            log.info("결제 성공 (userId={}, {} {}, {}원, orderId={})", userId, plan.code, cycle, payment.amount, payment.orderId)
        } catch (e: TossException) {
            if (e.uncertain) {
                log.error("결제 결과를 모름 (orderId={}): 토스 상점관리자에서 확인 필요", payment.orderId)
                throw BusinessException(ErrorCode.PAYMENT_UNCERTAIN)
            }
            log.info("결제 거절 (userId={}, orderId={}): {} {}", userId, payment.orderId, e.code, e.message)
            throw BusinessException(ErrorCode.PAYMENT_FAILED, mapOf("reason" to e.message), e.message)
        }
        return planService.workspaceSubscription(teamId, userId)
    }

    fun history(userId: Long): List<PaymentView> {
        val plans = mutableMapOf<Long, Plan>()
        val teamNames = mutableMapOf<Long, String?>()
        return payments.findTop50ByUserIdOrderByCreatedDtDesc(userId).map {
            val plan = plans.getOrPut(it.planId) { planService.planById(it.planId) }
            val supply = Math.round(it.amount * 10.0 / 11.0)
            PaymentView(
                orderId = it.orderId,
                orderName = it.orderName,
                planCode = plan.code,
                planName = plan.name,
                amount = it.amount,
                supplyAmount = supply,
                vat = it.amount - supply,
                status = it.status,
                billingCycle = it.billingCycle,
                method = it.method,
                cardCompany = it.cardCompany,
                cardNumberMasked = it.cardNumberMasked,
                approveNo = it.approveNo,
                receiptUrl = it.receiptUrl,
                failureMessage = it.failureMessage,
                approvedAt = it.approvedAt,
                periodEnd = it.periodEnd,
                createdAt = it.createdDt,
                teamId = it.teamId,
                teamName = it.teamId?.let { id -> teamNames.getOrPut(id) { teams.findTeamById(id)?.name } },
            )
        }
    }

    // ----------------------------------------------------------------- 갱신 (스케줄러)

    /**
     * - 자동결제가 꺼진 구독: 끝나기 3일 전에 알림, 끝나면 만료
     * - 자동결제 구독: 끝나면 결제. 실패하면 하루 뒤 다시 (3번 실패하면 만료)
     * @return 처리한 구독 수
     */
    fun processRenewals(now: LocalDateTime = LocalDateTime.now()): Int {
        val candidates = subscriptions.findAllByStatusInAndCurrentPeriodEndBefore(
            listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE),
            now.plusDays(EXPIRING_NOTICE_DAYS),
        )
        var handled = 0
        for (s in candidates) {
            runCatching {
                when {
                    s.status == SubscriptionStatus.ACTIVE && s.currentPeriodEnd!!.isAfter(now) -> {
                        if (s.expiringNotifiedAt == null && !willRenew(s)) {
                            tx.executeWithoutResult { notifyExpiring(reload(s), now) }
                            handled++
                        }
                    }
                    s.status == SubscriptionStatus.PAST_DUE && s.nextRetryAt?.isAfter(now) == true -> Unit
                    else -> {
                        renew(s, now)
                        handled++
                    }
                }
            }.onFailure { log.error("구독 갱신 처리 실패 (subscriptionId={})", s.id, it) }
        }
        return handled
    }

    private fun renew(s: Subscription, now: LocalDateTime) {
        val card = billingKeys.findByUserIdAndActiveTrue(s.userId)
        val plan = planService.planById(s.planId)
        val cycle = s.billingCycle ?: BillingCycle.MONTHLY
        val team = s.teamId?.let(teams::findTeamById)?.takeIf { it.status == TeamStatus.ACTIVE }
        if (!s.autoRenew || card == null || plan.isFree || team == null) {
            tx.executeWithoutResult { expire(reload(s), plan, now) }
            return
        }
        val payment = tx.execute { payments.save(newPayment(s.userId, team.id!!, team.name, plan, cycle, card)) }!!
        try {
            chargeAndApply(payment.orderId, card) { at -> reload(s).also { it.extend(cycle, at) } }
            log.info("자동결제 성공 (subscriptionId={}, {} {})", s.id, plan.code, cycle)
        } catch (e: TossException) {
            if (e.uncertain) {
                // 결제됐는지 모름: 다음 주기에 같은 구독을 다시 시도하지 않도록 재시도 시각만 미룬다
                log.error("자동결제 결과를 모름 (orderId={}): 토스 상점관리자에서 확인 필요", payment.orderId)
            }
            tx.executeWithoutResult {
                val sub = reload(s)
                sub.paymentFailed(now)
                if (sub.failedAttempts >= Subscription.MAX_PAYMENT_ATTEMPTS) {
                    expire(sub, plan, now)
                } else {
                    notifyUser(sub, NotificationType.PAYMENT_FAILED, plan, sub.nextRetryAt, PlanMailKind.PAYMENT_FAILED, e.message)
                }
            }
        }
    }

    private fun willRenew(s: Subscription) = s.autoRenew && billingKeys.findByUserIdAndActiveTrue(s.userId) != null

    private fun notifyExpiring(s: Subscription, now: LocalDateTime) {
        if (s.expiringNotifiedAt != null) return
        s.markExpiringNotified(now)
        notifyUser(s, NotificationType.PLAN_EXPIRING, planService.planById(s.planId), s.currentPeriodEnd, PlanMailKind.EXPIRING, null)
    }

    private fun expire(s: Subscription, plan: Plan, now: LocalDateTime) {
        s.expire(now)
        notifyUser(s, NotificationType.PLAN_EXPIRED, plan, s.currentPeriodEnd, PlanMailKind.EXPIRED, null)
    }

    // -----------------------------------------------------------------

    /** 결제 → 성공하면 같은 트랜잭션에서 결제 승인 기록 + 구독 반영. 실패하면 결제 실패 기록을 남기고 TossException 을 다시 던진다 */
    private fun chargeAndApply(orderId: String, card: BillingKey, apply: (LocalDateTime) -> Subscription) {
        val payment = payments.findByOrderId(orderId)!!
        val user = users.findUserById(payment.userId)
        val result = try {
            toss.charge(cipher.decrypt(card.billingKey), card.customerKey, payment.amount, payment.orderId, payment.orderName, user?.email, user?.realName ?: user?.nickname)
        } catch (e: TossException) {
            if (!e.uncertain) tx.executeWithoutResult { payments.findByOrderId(orderId)!!.fail(e.code, e.message) }
            throw e
        }
        tx.executeWithoutResult {
            val now = LocalDateTime.now()
            val p = payments.findByOrderId(orderId)!!
            p.approve(
                result.paymentKey, result.method, result.receiptUrl, now,
                cardCompany = result.cardCompany ?: card.cardCompany,
                cardNumberMasked = result.cardNumber ?: card.cardNumberMasked,
                approveNo = result.approveNo,
            )
            val subscription = apply(now)
            p.linkSubscription(subscription.id!!, subscription.currentPeriodEnd)
        }
    }

    private fun newPayment(userId: Long, teamId: Long, teamName: String, plan: Plan, cycle: BillingCycle, card: BillingKey) = Payment(
        userId = userId,
        planId = plan.id!!,
        orderId = "ORD-" + UUID.randomUUID().toString().replace("-", ""),
        // 토스 주문명은 100자까지
        orderName = "MQ ${plan.name} (${if (cycle == BillingCycle.YEARLY) "1년" else "1개월"}) · $teamName".take(100),
        amount = plan.price(cycle),
        billingKeyId = card.id,
        billingCycle = cycle,
        teamId = teamId,
    )

    private fun reload(s: Subscription) = subscriptions.findSubscriptionById(s.id!!)!!

    /** 결제한 사람에게 알림·메일 (워크스페이스 이름과 함께) */
    private fun notifyUser(s: Subscription, type: NotificationType, plan: Plan, date: LocalDateTime?, kind: PlanMailKind, reason: String?) {
        val teamName = s.teamId?.let(teams::findTeamById)?.name
        val path = BILLING_PATH + (s.teamId?.let { "?team=$it" } ?: "")
        notifications.notify(
            listOf(s.userId),
            type,
            mapOf(
                "planCode" to plan.code, "planName" to plan.name, "teamName" to teamName,
                "date" to date?.let(JacksonConfig::toOffsetString), "reason" to reason,
            ),
            path,
        )
        val user = users.findUserById(s.userId) ?: return
        if (user.email.isBlank()) return
        val label = teamName?.let { "${plan.name} ($it)" } ?: plan.name
        mail.sendAfterCommit(MailTemplates.planNotice(user.email, MailLanguage.KO, kind, label, date, reason, web.link(path)))
    }

    private fun requireEnabled() {
        if (!tossProperties.enabled) throw BusinessException(ErrorCode.PAYMENT_NOT_CONFIGURED)
    }

    companion object {
        const val EXPIRING_NOTICE_DAYS = 3L
        const val BILLING_PATH = "/app/billing"
    }
}
