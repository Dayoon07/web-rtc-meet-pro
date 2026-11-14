package com.webrtcmeetpro.domain.common.dto.res

/**
 * code   : 에러 종류 = 번역 키 (errors.<code>)
 * params : 번역 문구 보간용 ({{max}} 등)
 * errorCode / reason : 예상하지 못한 에러(500)일 때만.
 *   errorCode = 이 에러 1건의 번호 (ERR-YYYYMMDD-...). 사용자가 문의할 때 알려 주고, admin_error_logs 에서 찾는다
 *   reason    = 대략적인 원인 분류 (DB_CONNECTION 등). 화면은 이것으로 "무엇 때문인지" 를 쉬운 말로 보여 준다.
 *               예외 메시지·테이블 이름·스택 같은 내부 정보는 응답에 넣지 않는다.
 */
data class ErrorResDto(
    val code: String,
    val message: String,
    val params: Map<String, Any> = emptyMap(),
    val field: String? = null,
    val errorCode: String? = null,
    val reason: String? = null,
)
