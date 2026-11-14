package com.webrtcmeetpro.domain.plan.dto.req

/** 한도 초과 허용. 켤 때는 안내(1명당 월 요금, 결제일 멤버 수 기준)에 동의(agree = true)해야 한다 */
data class OverageReqDto(val allowed: Boolean, val agree: Boolean = false)
