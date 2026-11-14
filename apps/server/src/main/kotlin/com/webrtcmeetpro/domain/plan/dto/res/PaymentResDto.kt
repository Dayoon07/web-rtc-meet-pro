package com.webrtcmeetpro.domain.plan.dto.res

import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.PaymentKind
import com.webrtcmeetpro.domain.plan.PaymentStatus
import java.time.LocalDateTime

data class PaymentResDto(
    val orderId: String,
    val orderName: String,
    val planCode: String,
    val planName: String,
    val amount: Long,
    /** 공급가액 = 금액 × 10/11 (반올림), 부가세 = 금액 - 공급가액 */
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
    val kind: PaymentKind = PaymentKind.PLAN,
    val overageMembers: Int = 0,
    val overageAmount: Long = 0,
    /** 환불 안 했으면 0 · null */
    val refundedAmount: Long = 0,
    val refundedAt: LocalDateTime? = null,
    val refundReason: String? = null,
)
