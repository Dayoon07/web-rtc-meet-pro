package com.webrtcmeetpro.common.error

/** @param params 번역 문구에 끼워 넣을 값 (예: { "max": 3 } → "팀은 최대 {{max}}개까지") */
class BusinessException(
    val errorCode: ErrorCode,
    val params: Map<String, Any> = emptyMap(),
    message: String = errorCode.message,
) : RuntimeException(message)
