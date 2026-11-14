package com.webrtcmeetpro.domain.admin.dto.req

/** 고칠(추가할) 값. 컬럼 이름 → 값 (빈 문자열·null = 비우기) */
data class AdminRowValuesReqDto(val values: Map<String, Any?> = emptyMap())
