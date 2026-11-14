package com.webrtcmeetpro.domain.plan.dto.res

import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import java.time.LocalDateTime

data class SubscriptionResDto(
    val team: WorkspaceRefResDto,
    /** 플랜이 소유자의 것이라 소유자가 만든 모든 팀 */
    val appliesTo: List<WorkspaceRefResDto>,
    val plan: PlanResDto,
    val status: SubscriptionStatus,
    val startedAt: LocalDateTime?,
    /** FREE 는 null */
    val currentPeriodEnd: LocalDateTime?,
    /** 여러 팀에 있어도 한 명 */
    val memberCount: Long,
    val subscriptions: List<SubscriptionItemResDto>,
    val canManage: Boolean,
    val card: CardResDto?,
    /** 토스 키가 없으면 결제 버튼을 막는다 */
    val paymentsEnabled: Boolean,
)
