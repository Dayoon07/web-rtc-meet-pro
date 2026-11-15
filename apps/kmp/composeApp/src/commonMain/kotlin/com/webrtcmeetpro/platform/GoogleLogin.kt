package com.webrtcmeetpro.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 구글 로그인이 끝나고 서버가 앱으로 돌려준 결과: 1회용 코드, 또는 실패 이유(오류 코드) */
data class OAuthResult(val code: String?, val error: String?)

/** 브라우저에서 구글 로그인을 연다. 결과는 [OAuthCallbacks] 로 들어온다 */
fun interface GoogleLoginLauncher {
    fun start(serverUrl: String)
}

/**
 * 브라우저에서 돌아온 결과를 로그인 화면에 넘긴다.
 * Android 는 브라우저에 있는 동안 앱이 다시 만들어질 수 있어서, 화면이 나중에 생겨도 받을 수 있게 값으로 들고 있는다.
 */
object OAuthCallbacks {
    private val _pending = MutableStateFlow<OAuthResult?>(null)
    val pending: StateFlow<OAuthResult?> = _pending.asStateFlow()

    /** 예: mq://oauth/callback?code=… 또는 …?error=EMAIL_REGISTERED_WITH_PASSWORD */
    fun deliver(uri: String) {
        val params = uri.substringAfter('?', "").split('&').mapNotNull { pair ->
            pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to decodeQuery(it[1]) }
        }.toMap()
        if (params["code"] != null || params["error"] != null) _pending.value = OAuthResult(params["code"], params["error"])
    }

    fun consume(): OAuthResult? = _pending.value.also { _pending.value = null }
}

/** 알림을 눌러서 앱이 열렸을 때 갈 화면 (웹과 같은 주소, 예: /app/chat/1/1106) */
object PendingLinks {
    private val _link = MutableStateFlow<String?>(null)
    val link: StateFlow<String?> = _link.asStateFlow()

    fun open(link: String?) {
        if (!link.isNullOrBlank()) _link.value = link
    }

    fun consume(): String? = _link.value.also { _link.value = null }
}

private fun decodeQuery(value: String): String = buildString {
    var i = 0
    val bytes = mutableListOf<Byte>()
    fun flush() {
        if (bytes.isNotEmpty()) append(bytes.toByteArray().decodeToString()).also { bytes.clear() }
    }
    while (i < value.length) {
        val c = value[i]
        when {
            c == '%' && i + 2 <= value.lastIndex -> {
                bytes += value.substring(i + 1, i + 3).toInt(16).toByte()
                i += 3
                continue
            }
            c == '+' -> { flush(); append(' ') }
            else -> { flush(); append(c) }
        }
        i++
    }
    flush()
}
