package com.webrtcmeetpro.domain.plan.dto.res

import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import java.time.LocalDateTime

/** 기간이 남았거나 자동결제를 다시 시도 중인 유료 구독 */
data class SubscriptionItemResDto(
    val id: Long,
    val plan: PlanResDto,
    val status: SubscriptionStatus,
    val billingCycle: BillingCycle?,
    val autoRenew: Boolean,
    val startedAt: LocalDateTime,
    val currentPeriodEnd: LocalDateTime?,
    /** 여러 개면 가장 높은 플랜 하나만 true */
    val inUse: Boolean,
    val failedAttempts: Int,
    val nextRetryAt: LocalDateTime?,
    /** 결제한 사람만 자동결제를 켜고 끌 수 있다 */
    val mine: Boolean,
)
