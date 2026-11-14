package com.webrtcmeetpro.domain.plan.dto.req

import jakarta.validation.constraints.Size

/** 환불 요청. reason = 사유(선택), agree = "환불하면 플랜이 바로 끝나요" 확인 */
data class RefundReqDto(@field:Size(max = 200) val reason: String? = null, val agree: Boolean? = null)
