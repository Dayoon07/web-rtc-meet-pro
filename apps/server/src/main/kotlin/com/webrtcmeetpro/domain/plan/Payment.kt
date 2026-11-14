package com.webrtcmeetpro.domain.plan

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.YesNoConverter
import java.time.LocalDateTime

enum class PaymentStatus { READY, DONE, CANCELED, FAILED, ABORTED }

/** PLAN 에도 월 자동결제면 초과 인원 요금이 같이 들어갈 수 있다 */
enum class PaymentKind { PLAN, OVERAGE }

/** order_id 는 토스 호출 전에 우리가 만들어 결과를 모를 때도 토스에서 찾을 수 있게 한다 */
@Entity
@Table(name = "payments")
class Payment(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val planId: Long,

    @Column(nullable = false, unique = true)
    val orderId: String,

    @Column(nullable = false)
    val orderName: String,

    @Column(nullable = false)
    val amount: Long,

    var billingKeyId: Long? = null,

    @Enumerated(EnumType.STRING)
    val billingCycle: BillingCycle? = null,

    val teamId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val paymentKind: PaymentKind = PaymentKind.PLAN,

    /** 영수증에 따로 보여 주려고 나눠 둔다 */
    @Column(nullable = false)
    val overageMembers: Int = 0,

    @Column(nullable = false)
    val overageAmount: Long = 0,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    var id: Long? = null
        protected set

    var subscriptionId: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.READY
        protected set

    var method: String? = null
        protected set

    var paymentKey: String? = null
        protected set

    var receiptUrl: String? = null
        protected set

    var failureCode: String? = null
        protected set

    var failureMessage: String? = null
        protected set

    var approvedAt: LocalDateTime? = null
        protected set

    var canceledAt: LocalDateTime? = null
        protected set

    /** 이 결제로 늘어난 구독 기간 끝. 환불할 때 쓴 날수를 이걸로 계산한다 */
    var periodEnd: LocalDateTime? = null
        protected set

    var cardCompany: String? = null
        protected set

    var cardNumberMasked: String? = null
        protected set

    var approveNo: String? = null
        protected set

    /** 부분 환불도 있어서 amount 와 따로 둔다. 시각은 canceled_at */
    @Column(nullable = false)
    var refundedAmount: Long = 0
        protected set

    var refundReason: String? = null
        protected set

    var refundedBy: Long? = null
        protected set

    fun approve(
        paymentKey: String?,
        method: String?,
        receiptUrl: String?,
        at: LocalDateTime = LocalDateTime.now(),
        cardCompany: String? = null,
        cardNumberMasked: String? = null,
        approveNo: String? = null,
    ) {
        check(status == PaymentStatus.READY) { "READY 상태에서만 승인할 수 있습니다." }
        this.cardCompany = cardCompany?.take(30)
        this.cardNumberMasked = cardNumberMasked?.take(30)
        this.approveNo = approveNo?.take(30)
        this.paymentKey = paymentKey
        this.method = method
        this.receiptUrl = receiptUrl
        this.approvedAt = at
        this.status = PaymentStatus.DONE
    }

    fun fail(code: String?, message: String?) {
        failureCode = code
        failureMessage = message?.take(500)
        status = PaymentStatus.FAILED
    }

    fun linkSubscription(subscriptionId: Long, periodEnd: LocalDateTime? = null) {
        this.subscriptionId = subscriptionId
        this.periodEnd = periodEnd
    }

    fun cancel(at: LocalDateTime = LocalDateTime.now()) {
        status = PaymentStatus.CANCELED
        canceledAt = at
    }

    /** 토스 취소가 끝난 뒤에 부른다. 부분 환불도 CANCELED */
    fun refund(amount: Long, reason: String?, by: Long, at: LocalDateTime = LocalDateTime.now()) {
        check(status == PaymentStatus.DONE) { "승인된 결제만 환불할 수 있습니다." }
        require(amount in 1..this.amount) { "환불 금액은 1원 ~ 결제 금액입니다." }
        refundedAmount = amount
        refundReason = reason?.take(200)
        refundedBy = by
        cancel(at)
    }
}

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findByOrderId(orderId: String): Payment?
    fun findAllByUserIdOrderByCreatedDtDesc(userId: Long): List<Payment>
    fun findTop50ByUserIdOrderByCreatedDtDesc(userId: Long): List<Payment>
    fun findAllBySubscriptionIdOrderByIdDesc(subscriptionId: Long): List<Payment>
}

/** billingKey 는 애플리케이션에서 암호화한 값 */
@Entity
@Table(name = "billing_keys")
class BillingKey(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false, unique = true)
    val customerKey: String,

    @Column(nullable = false)
    val billingKey: String,

    val cardCompany: String? = null,
    val cardNumberMasked: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "billing_key_id")
    var id: Long? = null
        protected set

    @Convert(converter = YesNoConverter::class)
    @Column(nullable = false)
    var active: Boolean = true
        protected set

    var revokedAt: LocalDateTime? = null
        protected set

    fun revoke(at: LocalDateTime = LocalDateTime.now()) {
        active = false
        revokedAt = at
    }
}

interface BillingKeyRepository {
    fun save(billingKey: BillingKey): BillingKey
    fun findByUserIdAndActiveTrue(userId: Long): BillingKey?
    fun findAllByUserIdAndActiveTrue(userId: Long): List<BillingKey>
    fun findBillingKeyById(id: Long): BillingKey?
}
