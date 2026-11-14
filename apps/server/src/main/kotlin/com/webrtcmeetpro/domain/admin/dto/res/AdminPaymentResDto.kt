package com.webrtcmeetpro.domain.admin.dto.res

import java.time.LocalDateTime

data class AdminPaymentResDto(
    val orderId: String,
    val orderName: String,
    val amount: Long,
    val status: String,
    val paymentKind: String,
    val billingCycle: String?,
    val userId: Long,
    val userEmail: String?,
    val teamName: String?,
    val approvedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val refundedAmount: Long,
    /** payments.canceled_at */
    val refundedAt: LocalDateTime?,
    val refundReason: String?,
    /** 토스 결제 키가 없는 테스트 데이터는 환불할 수 없다 */
    val hasPaymentKey: Boolean,
)
