package com.webrtcmeetpro.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.first
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

    /** 안내 창(앱 루트)이 화면에 붙을 때까지 기다린다. 앱 시작 직후의 신고가 버려지지 않게 */
    suspend fun awaitListener() {
        _events.subscriptionCount.first { it > 0 }
    }
}

/**
 * 서버 에러도, 네트워크 끊김도 아닌 "예상하지 못한 앱 오류"를 받는 곳.
 * 앱(AppErrorReporter)이 handler 를 달아 두고 서버로 신고 + 에러 코드 안내 창을 띄운다.
 * 화면들은 실패하면 errorCode 를 읽으므로 거기서 한 번 알린다 (같은 예외는 한 번만).
 */
object UnknownErrors {
    var handler: ((Throwable) -> Unit)? = null
    private val seen = ArrayDeque<Throwable>()

    internal fun notify(e: Throwable) {
        val h = handler ?: return
        if (seen.any { it === e }) return
        seen.addLast(e)
        if (seen.size > 20) seen.removeFirst()
        runCatching { h(e) }
    }
}

/** 화면에서 번역 문구를 고를 때 쓰는 코드. 서버 에러 / 네트워크 끊김 / 그 밖 */
val Throwable.errorCode: String
    get() = when (this) {
        is ApiException -> code
        is IOException -> "NETWORK"
        // 화면을 떠나서 작업이 취소된 것: 오류가 아니다
        is CancellationException -> "UNKNOWN"
        else -> "UNKNOWN".also { UnknownErrors.notify(this) }
    }
