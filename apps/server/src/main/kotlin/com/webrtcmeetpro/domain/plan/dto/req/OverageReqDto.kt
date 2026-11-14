package com.webrtcmeetpro.domain.plan.dto.req

/** 켤 때는 1명당 월 요금 안내에 동의(agree)해야 한다 */
data class OverageReqDto(val allowed: Boolean, val agree: Boolean = false)
