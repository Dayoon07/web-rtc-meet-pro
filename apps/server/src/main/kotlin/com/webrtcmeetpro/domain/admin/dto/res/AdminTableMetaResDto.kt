package com.webrtcmeetpro.domain.admin.dto.res

data class AdminTableMetaResDto(
    val name: String,
    val label: String,
    val group: String,
    val description: String?,
    val primaryKey: String?,
    val readOnly: Boolean,
    val insertable: Boolean,
    /** deleted_yn 컬럼이 있어서 "사용/미사용" 필터를 쓸 수 있는지 */
    val softDelete: Boolean,
    val columns: List<AdminColumnResDto>,
)
