package com.webrtcmeetpro.domain.plan.dto.res

import java.time.LocalDateTime

data class ExpiryNoticeResDto(
    val subscriptionId: Long,
    val plan: PlanResDto,
    val expiredAt: LocalDateTime?,
    val teamId: Long?,
    val teamName: String?,
    val currentPlan: PlanResDto,
)
