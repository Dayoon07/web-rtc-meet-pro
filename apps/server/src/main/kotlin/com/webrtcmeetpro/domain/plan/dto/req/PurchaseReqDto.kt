package com.webrtcmeetpro.domain.plan.dto.req

import com.webrtcmeetpro.domain.plan.BillingCycle
import jakarta.validation.constraints.NotBlank

/** 전자상거래법 제8조: 주문 내용·자동결제·환불 규정을 확인했다는 동의를 받아야 한다 */
data class PurchaseReqDto(@field:NotBlank val planCode: String, val cycle: BillingCycle, val autoRenew: Boolean, val agree: Boolean = false)
