package com.webrtcmeetpro.domain.plan.dto.res

import java.time.LocalDateTime

/** 인원은 소유자의 모든 팀 합계 */
data class OverageResDto(
    val teamId: Long,
    val allowed: Boolean,
    val agreedAt: LocalDateTime?,
    val members: Long,
    val limit: Int,
    val excess: Long,
    val pricePerMember: Long,
    /** 지금 인원이면 다음 결제에 더해질 금액 */
    val nextAmount: Long,
    val nextChargeAt: LocalDateTime?,
    /** false 면 한 달마다 따로 결제한다 */
    val withRenewal: Boolean,
    val canManage: Boolean,
    /** 켜려면 카드가 있어야 한다 */
    val hasCard: Boolean,
    val hardCap: Int,
)
