package com.webrtcmeetpro.domain.admin.dto.req

/** 관리자 환불: amount = 환불 금액 (없으면 약관대로 계산한 금액), reason = 사유 */
data class AdminRefundReqDto(val amount: Long? = null, val reason: String? = null)
