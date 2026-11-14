package com.webrtcmeetpro.domain.admin.dto.req

/** amount 가 없으면 약관대로 계산한 금액 */
data class AdminRefundReqDto(val amount: Long? = null, val reason: String? = null)
