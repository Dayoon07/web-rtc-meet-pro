package com.webrtcmeetpro.domain.plan.dto.res

import java.time.LocalDateTime

/** 요금제 화면의 "한도 초과" 칸 (소유자의 모든 팀 합계) */
data class OverageResDto(
    val teamId: Long,
    /** 소유자가 한도 초과를 허용했는지 (= 초과 요금 동의) */
    val allowed: Boolean,
    val agreedAt: LocalDateTime?,
    val members: Long,
    val limit: Int,
    /** 지금 한도를 넘은 인원 */
    val excess: Long,
    val pricePerMember: Long,
    /** 지금 인원이면 다음 결제에 더해질 금액 */
    val nextAmount: Long,
    /** 초과 요금이 들어갈 다음 결제일 (월 자동결제면 플랜 갱신일) */
    val nextChargeAt: LocalDateTime?,
    /** 월 자동결제 플랜 갱신 때 같이 내는지 (아니면 한 달마다 따로) */
    val withRenewal: Boolean,
    /** 소유자만 바꿀 수 있다 */
    val canManage: Boolean,
    /** 소유자 카드가 등록돼 있는지 (켜려면 필요) */
    val hasCard: Boolean,
    /** 소유자의 모든 팀을 합친 최대 인원 */
    val hardCap: Int,
)
