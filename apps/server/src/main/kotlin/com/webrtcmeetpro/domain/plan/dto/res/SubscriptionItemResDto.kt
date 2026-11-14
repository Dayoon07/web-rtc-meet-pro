package com.webrtcmeetpro.domain.plan.dto.res

import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import java.time.LocalDateTime

/** 워크스페이스의 유료 구독 하나 (기간이 남았거나 자동결제를 다시 시도 중인 것) */
data class SubscriptionItemResDto(
    val id: Long,
    val plan: PlanResDto,
    val status: SubscriptionStatus,
    val billingCycle: BillingCycle?,
    val autoRenew: Boolean,
    val startedAt: LocalDateTime,
    val currentPeriodEnd: LocalDateTime?,
    /** 지금 적용 중인 구독인지 (여러 개면 가장 높은 플랜 하나) */
    val inUse: Boolean,
    val failedAttempts: Int,
    val nextRetryAt: LocalDateTime?,
    /** 보는 사람이 결제한 구독인지 (자동결제를 켜고 끌 수 있는 사람) */
    val mine: Boolean,
)
