package com.webrtcmeetpro.core.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.io.IOException

/**
 * 서버가 돌려준 에러. code 는 서버 ErrorCode 이름 (예: INVALID_CREDENTIALS).
 * supportCode / reason 은 예상하지 못한 에러(500)일 때만 — 서버 응답의 errorCode(ERR-...) 와 원인 분류.
 */
class ApiException(
    val status: Int,
    val code: String,
    message: String,
    val params: Map<String, String> = emptyMap(),
    val supportCode: String? = null,
    val reason: String? = null,
) : RuntimeException(message)

/** 서버가 기록한 예상하지 못한 에러 1건: 사용자에게 보여 줄 에러 코드 + 원인 분류 */
data class UnexpectedError(val errorCode: String, val reason: String)

/** 500 응답이 오면 HttpClient 가 여기로 보내고, 앱 루트가 받아서 안내 창(문의 이메일 + 에러 코드)을 띄운다 */
object UnexpectedErrors {
    private val _events = MutableSharedFlow<UnexpectedError>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<UnexpectedError> = _events.asSharedFlow()

    fun report(error: UnexpectedError) {
        _events.tryEmit(error)
    }
}

/** 화면에서 번역 문구를 고를 때 쓰는 코드. 서버 에러 / 네트워크 끊김 / 그 밖 */
val Throwable.errorCode: String
    get() = when (this) {
        is ApiException -> code
        is IOException -> "NETWORK"
        else -> "UNKNOWN"
    }
