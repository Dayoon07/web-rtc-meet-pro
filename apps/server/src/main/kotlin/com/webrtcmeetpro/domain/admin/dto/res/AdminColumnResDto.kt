package com.webrtcmeetpro.domain.admin.dto.res

data class AdminColumnResDto(
    val name: String,
    /** COMMENT ON COLUMN */
    val comment: String?,
    val kind: AdminColumnKind,
    val dbType: String,
    val maxLength: Int?,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val editable: Boolean,
    /** 값 대신 있음·없음만 내려간다 */
    val secret: Boolean,
    /** CHECK (col IN (...)) 에서 읽은 값 */
    val options: List<String>,
    val references: AdminColumnRefResDto?,
    /** PK 에서 "이 회원의 팀원 기록 보기" 처럼 이동할 때 */
    val referencedBy: List<AdminColumnRefResDto>,
)
