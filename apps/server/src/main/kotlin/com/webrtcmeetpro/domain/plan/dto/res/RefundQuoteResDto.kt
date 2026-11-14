package com.webrtcmeetpro.domain.plan.dto.res

import com.webrtcmeetpro.domain.plan.RefundRule
import java.time.LocalDateTime

/** 환불 미리 계산 (약관 제8조). 사용자·관리자가 환불하기 전에 본다 */
data class RefundQuoteResDto(
    val orderId: String,
    val orderName: String,
    val planName: String,
    val paidAmount: Long,
    val approvedAt: LocalDateTime?,
    val periodEnd: LocalDateTime?,
    val refundable: Boolean,
    /** 약관대로 계산한 환불 금액 */
    val refundAmount: Long,
    val rule: RefundRule,
    /** 이 결제 기간 중 쓴 날 / 전체 날 (PRORATED 일 때) */
    val usedDays: Int,
    val periodDays: Int,
    /** 환불할 수 없을 때 이유 (오류 코드: REFUND_ALREADY_DONE · REFUND_NOT_LATEST · ...) */
    val blockedBy: String?,
    /** 환불하면 함께 끝나는 플랜이 지금 쓰이는 중인지 */
    val cancelsPlan: Boolean,
)
