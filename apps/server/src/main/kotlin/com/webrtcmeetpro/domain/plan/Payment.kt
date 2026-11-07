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

/**
 * 결제 건 (토스페이먼츠).
 * order_id 는 결제창 호출 전에 우리가 발급하고, 승인 성공 시 payment_key / receipt_url 을 채운다.
 */
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

    /** 이 결제로 늘어난 구독 기간 끝 */
    var periodEnd: LocalDateTime? = null
        protected set

    fun approve(paymentKey: String?, method: String?, receiptUrl: String?, at: LocalDateTime = LocalDateTime.now()) {
        check(status == PaymentStatus.READY) { "READY 상태에서만 승인할 수 있습니다." }
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
}

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findByOrderId(orderId: String): Payment?
    fun findAllByUserIdOrderByCreatedDtDesc(userId: Long): List<Payment>
    fun findTop50ByUserIdOrderByCreatedDtDesc(userId: Long): List<Payment>
}

/** 토스 자동결제 빌링키. billingKey 는 애플리케이션에서 암호화한 값 */
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
