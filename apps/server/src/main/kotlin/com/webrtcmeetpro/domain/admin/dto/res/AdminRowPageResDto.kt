package com.webrtcmeetpro.domain.admin.dto.res

data class AdminRowPageResDto(val rows: List<Map<String, Any?>>, val total: Long, val page: Int, val size: Int)
