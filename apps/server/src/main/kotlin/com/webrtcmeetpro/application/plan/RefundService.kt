package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.domain.plan.RefundRule
import com.webrtcmeetpro.domain.plan.dto.res.RefundQuoteResDto
import com.webrtcmeetpro.application.notification.NotificationService
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.Payment
import com.webrtcmeetpro.domain.plan.PaymentKind
import com.webrtcmeetpro.domain.plan.PaymentRepository
import com.webrtcmeetpro.domain.plan.PaymentStatus
import com.webrtcmeetpro.domain.plan.SubscriptionRepository
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.external.toss.TossException
import com.webrtcmeetpro.external.toss.TossPaymentsClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * 환불 (약관 제8조): 7일 안이면 전액, 그 뒤에는 쓴 날수만큼 빼고 돌려준다.
 * 사용자는 구독의 가장 최근 결제만 약관 금액으로, 관리자는 어떤 결제든 금액을 정해서 환불한다.
 */
@Service
class RefundService(
    private val toss: TossPaymentsClient,
    private val payments: PaymentRepository,
    private val subscriptions: SubscriptionRepository,
    private val planService: PlanService,
    private val teams: TeamRepository,
    private val notifications: NotificationService,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    fun quoteForUser(orderId: String, userId: Long, now: LocalDateTime = LocalDateTime.now()): RefundQuoteResDto =
        quote(requireMine(orderId, userId), now, byAdmin = false)

    fun quoteForAdmin(orderId: String, now: LocalDateTime = LocalDateTime.now()): RefundQuoteResDto =
        quote(requirePayment(orderId), now, byAdmin = true)

    fun refundByUser(orderId: String, userId: Long, reason: String?, now: LocalDateTime = LocalDateTime.now()): RefundQuoteResDto {
        val payment = requireMine(orderId, userId)
        val q = quote(payment, now, byAdmin = false)
        if (!q.refundable) throw BusinessException(ErrorCode.REFUND_NOT_ALLOWED, mapOf("reason" to (q.blockedBy ?: "")))
        execute(payment.orderId, q.refundAmount, reason?.trim()?.ifEmpty { null } ?: "고객 요청", userId, now)
        return quote(requirePayment(orderId), now, byAdmin = false)
    }

    /** amount 가 없으면 약관대로 계산한 금액 */
    fun refundByAdmin(orderId: String, adminId: Long, amount: Long?, reason: String?, now: LocalDateTime = LocalDateTime.now()): RefundQuoteResDto {
        val payment = requirePayment(orderId)
        val q = quote(payment, now, byAdmin = true)
        if (q.blockedBy == ErrorCode.REFUND_ALREADY_DONE.name || q.blockedBy == ErrorCode.REFUND_NO_PAYMENT_KEY.name) {
            throw BusinessException(ErrorCode.REFUND_NOT_ALLOWED, mapOf("reason" to (q.blockedBy ?: "")))
        }
        val value = amount ?: q.refundAmount
        if (value <= 0 || value > payment.amount) throw BusinessException(ErrorCode.INVALID_REFUND_AMOUNT, mapOf("max" to payment.amount))
        execute(payment.orderId, value, reason?.trim()?.ifEmpty { null } ?: "관리자 환불", adminId, now)
        return quote(requirePayment(orderId), now, byAdmin = true)
    }

    private fun quote(payment: Payment, now: LocalDateTime, byAdmin: Boolean): RefundQuoteResDto {
        val plan = planService.planById(payment.planId)
        val subscription = payment.subscriptionId?.let(subscriptions::findSubscriptionById)
        val cancelsPlan = subscription?.isLive == true
        val base = RefundQuoteResDto(
            orderId = payment.orderId, orderName = payment.orderName, planName = plan.name, paidAmount = payment.amount,
            approvedAt = payment.approvedAt, periodEnd = payment.periodEnd, refundable = false, refundAmount = 0,
            rule = RefundRule.NONE, usedDays = 0, periodDays = 0, blockedBy = null, cancelsPlan = cancelsPlan,
        )
        fun blocked(code: ErrorCode) = base.copy(blockedBy = code.name)

        if (payment.status != PaymentStatus.DONE) return blocked(if (payment.status == PaymentStatus.CANCELED) ErrorCode.REFUND_ALREADY_DONE else ErrorCode.REFUND_NOT_PAID)
        if (payment.paymentKey.isNullOrBlank()) return blocked(ErrorCode.REFUND_NO_PAYMENT_KEY)
        if (!byAdmin) {
            // 지난 기간은 이미 쓴 것이라 가장 최근 결제만 환불할 수 있다
            if (payment.paymentKind != PaymentKind.PLAN || subscription == null) return blocked(ErrorCode.REFUND_ASK_SUPPORT)
            if (!subscription.isLive) return blocked(ErrorCode.REFUND_PLAN_ENDED)
            val latest = payments.findAllBySubscriptionIdOrderByIdDesc(subscription.id!!).firstOrNull { it.status == PaymentStatus.DONE }
            if (latest?.id != payment.id) return blocked(ErrorCode.REFUND_NOT_LATEST)
        }

        val approvedAt = payment.approvedAt ?: return blocked(ErrorCode.REFUND_NOT_PAID)
        if (!now.isAfter(approvedAt.plusDays(FULL_REFUND_DAYS))) {
            return base.copy(refundable = true, refundAmount = payment.amount, rule = RefundRule.FULL_7_DAYS)
        }
        // 같은 플랜을 미리 사서 뒤에 이어 붙인 경우도 있어서, 시작일이 아니라 끝에서 한 주기 앞부터 센다
        val cycle = payment.billingCycle ?: BillingCycle.MONTHLY
        val periodEnd = payment.periodEnd ?: return if (byAdmin) base.copy(refundable = true, rule = RefundRule.PRORATED) else blocked(ErrorCode.REFUND_ASK_SUPPORT)
        val periodStart = if (cycle == BillingCycle.YEARLY) periodEnd.minusYears(1) else periodEnd.minusMonths(1)
        val periodDays = days(periodStart, periodEnd).coerceAtLeast(1)
        val usedDays = if (now.isBefore(periodStart)) 0 else days(periodStart, minOf(now, periodEnd)).coerceAtMost(periodDays)
        val usedCharge = if (cycle == BillingCycle.YEARLY) {
            (plan.monthlyPrice / 30.0 * usedDays).roundToLong()
        } else {
            (payment.amount.toDouble() * usedDays / periodDays).roundToLong()
        }
        val refund = (payment.amount - usedCharge).coerceIn(0, payment.amount)
        val q = base.copy(refundAmount = refund, rule = RefundRule.PRORATED, usedDays = usedDays, periodDays = periodDays)
        // 관리자는 계산이 0원이어도 금액을 정해서 환불할 수 있다
        return if (refund > 0 || byAdmin) q.copy(refundable = true) else q.copy(blockedBy = ErrorCode.REFUND_NOTHING_LEFT.name)
    }

    private fun execute(orderId: String, amount: Long, reason: String, actorId: Long, now: LocalDateTime) {
        val payment = requirePayment(orderId)
        val partial = amount < payment.amount
        try {
            // 두 번 눌러도 한 번만 취소되게 멱등 키를 준다
            toss.cancel(payment.paymentKey!!, reason.take(200), if (partial) amount else null, "refund-${payment.orderId}")
        } catch (e: TossException) {
            log.info("환불 실패 (orderId={}): {} {}", orderId, e.code, e.message)
            throw BusinessException(ErrorCode.REFUND_FAILED, mapOf("reason" to e.message), e.message)
        }
        tx.executeWithoutResult {
            val p = requirePayment(orderId)
            p.refund(amount, reason, actorId, now)
            p.subscriptionId?.let(subscriptions::findSubscriptionById)?.takeIf { it.isLive }?.cancel(now)
            val teamName = p.teamId?.let(teams::findTeamById)?.name
            notifications.notify(
                listOf(p.userId),
                NotificationType.PAYMENT_REFUNDED,
                mapOf("planName" to planService.planById(p.planId).name, "amount" to amount, "teamName" to teamName),
                "/app/billing",
            )
        }
        log.info("환불 (orderId={}, {}원{}, by={})", orderId, amount, if (partial) " 부분" else "", actorId)
    }

    private fun requirePayment(orderId: String): Payment = payments.findByOrderId(orderId) ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)

    private fun requireMine(orderId: String, userId: Long): Payment =
        requirePayment(orderId).takeIf { it.userId == userId } ?: throw BusinessException(ErrorCode.PAYMENT_NOT_FOUND)

    /** 1시간만 써도 하루로 친다 */
    private fun days(from: LocalDateTime, to: LocalDateTime): Int = ceil(Duration.between(from, to).toMinutes() / 1440.0).toInt().coerceAtLeast(0)

    companion object {
        /** 전자상거래법 청약철회 기간 */
        const val FULL_REFUND_DAYS = 7L
    }
}
