package com.webrtcmeetpro.domain.plan.dto.res

/** 카드 등록창을 열 때 필요한 값 (웹이 토스 SDK 에 넘긴다) */
data class CardRegistrationResDto(val clientKey: String, val customerKey: String, val customerEmail: String, val customerName: String)
