package com.webrtcmeetpro.domain.admin.dto.res

data class AdminTableSummaryResDto(
    val name: String,
    val label: String,
    val group: String,
    val description: String?,
    val rowCount: Long,
    val readOnly: Boolean,
    val insertable: Boolean,
)
