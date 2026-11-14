package com.webrtcmeetpro.domain.plan.dto.res

import com.webrtcmeetpro.domain.plan.RefundRule
import java.time.LocalDateTime

data class RefundQuoteResDto(
    val orderId: String,
    val orderName: String,
    val planName: String,
    val paidAmount: Long,
    val approvedAt: LocalDateTime?,
    val periodEnd: LocalDateTime?,
    val refundable: Boolean,
    val refundAmount: Long,
    val rule: RefundRule,
    /** PRORATED 일 때만 */
    val usedDays: Int,
    val periodDays: Int,
    /** 오류 코드 (REFUND_ALREADY_DONE 등) */
    val blockedBy: String?,
    /** 환불하면 함께 끝나는 플랜이 지금 쓰이는 중인지 */
    val cancelsPlan: Boolean,
)
