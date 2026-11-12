package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable

/**
 * 내가 결제한 내역 (GET /api/billing/payments). 앱은 결제 내역을 보고 환불만 한다 (새 결제는 웹 요금제 화면).
 * status: READY · DONE(승인) · CANCELED(환불·취소) · FAILED · ABORTED, kind: PLAN(플랜) · OVERAGE(초과 인원 요금)
 */
@Serializable
data class Payment(
    val orderId: String,
    val orderName: String,
    val planName: String = "",
    val amount: Long,
    val status: String,
    val approvedAt: String? = null,
    val periodEnd: String? = null,
    val createdAt: String,
    val teamName: String? = null,
    val kind: String = "PLAN",
    val refundedAmount: Long = 0,
    val refundedAt: String? = null,
) {
    /** 웹과 같이: 승인된 플랜 결제만 환불을 요청할 수 있다 (초과 인원 요금은 고객센터) */
    val refundable: Boolean get() = status == "DONE" && kind != "OVERAGE"
}

/**
 * 환불 미리 계산 (약관 제8조). rule: FULL_7_DAYS(7일 안 전액) · PRORATED(쓴 날 빼고) · NONE.
 * blockedBy: 환불할 수 없을 때 이유 (오류 코드)
 */
@Serializable
data class RefundQuote(
    val orderId: String,
    val orderName: String,
    val planName: String = "",
    val paidAmount: Long,
    val approvedAt: String? = null,
    val refundable: Boolean,
    val refundAmount: Long,
    val rule: String = "NONE",
    val usedDays: Int = 0,
    val periodDays: Int = 0,
    val blockedBy: String? = null,
    val cancelsPlan: Boolean = false,
)

/** 환불 요청: 사유(선택) + "환불하면 플랜이 바로 끝나요" 동의 */
@Serializable
data class RefundRequest(val reason: String? = null, val agree: Boolean)
