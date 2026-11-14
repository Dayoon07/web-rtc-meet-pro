package com.webrtcmeetpro.domain.plan.dto.req

import jakarta.validation.constraints.Size

/** agree = 환불하면 플랜이 바로 끝난다는 것을 확인 */
data class RefundReqDto(@field:Size(max = 200) val reason: String? = null, val agree: Boolean? = null)
