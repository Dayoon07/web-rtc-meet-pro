package com.webrtcmeetpro.domain.monitoring.dto.res

/** 확인 하나: 이름 · 정상인지 · 한 줄 설명 */
data class HealthCheckResDto(val name: String, val ok: Boolean, val detail: String)
