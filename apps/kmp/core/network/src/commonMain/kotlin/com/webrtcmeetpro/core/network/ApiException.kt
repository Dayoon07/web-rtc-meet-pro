package com.webrtcmeetpro.core.network

import kotlinx.io.IOException

/** 서버가 돌려준 에러. code 는 서버 ErrorCode 이름 (예: INVALID_CREDENTIALS) */
class ApiException(
    val status: Int,
    val code: String,
    message: String,
    val params: Map<String, String> = emptyMap(),
) : RuntimeException(message)

/** 화면에서 번역 문구를 고를 때 쓰는 코드. 서버 에러 / 네트워크 끊김 / 그 밖 */
val Throwable.errorCode: String
    get() = when (this) {
        is ApiException -> code
        is IOException -> "NETWORK"
        else -> "UNKNOWN"
    }
