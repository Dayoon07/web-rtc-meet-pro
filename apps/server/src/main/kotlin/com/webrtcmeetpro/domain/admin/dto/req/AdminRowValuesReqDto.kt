package com.webrtcmeetpro.domain.admin.dto.req

/** 빈 문자열·null 은 비우기 */
data class AdminRowValuesReqDto(val values: Map<String, Any?> = emptyMap())
