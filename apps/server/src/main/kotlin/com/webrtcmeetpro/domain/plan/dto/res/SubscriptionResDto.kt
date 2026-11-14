package com.webrtcmeetpro.domain.plan.dto.res

import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import java.time.LocalDateTime

/** 요금제 상태 (플랜은 팀 소유자의 것이라 그 사람이 만든 모든 팀에 같다) */
data class SubscriptionResDto(
    val team: WorkspaceRefResDto,
    /** 이 플랜이 적용되는 팀 = 소유자가 만든 모든 팀 */
    val appliesTo: List<WorkspaceRefResDto>,
    /** 지금 적용 중인 플랜 (유료 구독이 없으면 FREE) */
    val plan: PlanResDto,
    val status: SubscriptionStatus,
    val startedAt: LocalDateTime?,
    /** 적용 중인 구독의 기간 끝 (FREE 는 null) */
    val currentPeriodEnd: LocalDateTime?,
    /** 소유자의 모든 팀 사람 수 (여러 팀에 있어도 한 명) */
    val memberCount: Long,
    val subscriptions: List<SubscriptionItemResDto>,
    /** 결제할 수 있는 사람 (워크스페이스 소유자) */
    val canManage: Boolean,
    /** 보는 사람이 등록한 카드 */
    val card: CardResDto?,
    /** 토스 키가 설정돼 있어서 결제할 수 있는지 */
    val paymentsEnabled: Boolean,
)
