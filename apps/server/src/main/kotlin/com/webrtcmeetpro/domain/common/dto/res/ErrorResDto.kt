package com.webrtcmeetpro.domain.common.dto.res

/**
 * code 는 번역 키(errors.<code>), params 는 번역 문구에 끼워 넣을 값.
 * errorCode·reason 은 예상하지 못한 에러(500)일 때만 채운다. 내부 정보(예외 메시지·테이블 이름·스택)는 응답에 넣지 않고
 * 사용자는 errorCode 로 문의하고 관리자는 admin_error_logs 에서 찾는다.
 */
data class ErrorResDto(
    val code: String,
    val message: String,
    val params: Map<String, Any> = emptyMap(),
    val field: String? = null,
    val errorCode: String? = null,
    val reason: String? = null,
)
