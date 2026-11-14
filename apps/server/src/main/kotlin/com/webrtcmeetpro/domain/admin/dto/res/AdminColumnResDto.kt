package com.webrtcmeetpro.domain.admin.dto.res

data class AdminColumnResDto(
    val name: String,
    /** DB 주석(COMMENT ON COLUMN). 없으면 null */
    val comment: String?,
    val kind: AdminColumnKind,
    /** 예: VARCHAR2(255 CHAR), NUMBER(19), TIMESTAMP(6) */
    val dbType: String,
    /** 글자 컬럼의 최대 길이 */
    val maxLength: Int?,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val editable: Boolean,
    /** 비밀값: 값 대신 있음(true)/없음(false) 만 내려간다 */
    val secret: Boolean,
    /** CHECK (col IN (...)) 에서 읽은 허용 값 */
    val options: List<String>,
    /** FK 가 가리키는 테이블.컬럼 (화면에서 눌러서 이동) */
    val references: AdminColumnRefResDto?,
    /** 이 컬럼을 가리키는 다른 테이블들 (PK 에서 "이 회원의 팀원 기록 보기" 같은 이동) */
    val referencedBy: List<AdminColumnRefResDto>,
)
