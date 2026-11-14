package com.webrtcmeetpro.domain.plan.dto.res

import java.time.LocalDateTime

/** 아직 확인하지 않은 만료 → 웹·앱이 "플랜이 만료됐어요" 창을 띄운다 */
data class ExpiryNoticeResDto(
    val subscriptionId: Long,
    val plan: PlanResDto,
    val expiredAt: LocalDateTime?,
    val teamId: Long?,
    val teamName: String?,
    /** 그 워크스페이스에 지금 적용 중인 플랜 */
    val currentPlan: PlanResDto,
)
