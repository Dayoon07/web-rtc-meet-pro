package com.webrtcmeetpro.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 서버가 보낸 MESSAGE 프레임. destination 은 구독한 경로 (예: /user/queue/chat), body 는 JSON 문자열 */
data class StompMessage(val destination: String, val body: String)

/**
 * 서버(Spring simple broker)의 /ws 에 붙는 최소 STOMP 1.2 클라이언트.
 * 이 앱은 서버 → 앱 푸시만 받고 보내기는 REST 로 하므로 CONNECT / SUBSCRIBE / MESSAGE 만 다룬다.
 *
 * - CONNECT 헤더 Authorization: Bearer <access token> 으로 인증
 * - 토큰 만료로 거절되면 refreshAuth() (아무 인증 API 호출 → Ktor 가 401 에서 토큰 회전) 후 다시 연결
 * - 끊기면 1초 → 최대 30초 간격으로 계속 다시 연결. run() 을 부른 코루틴을 취소하면 끝난다
 */
class StompClient(
    private val client: HttpClient,
    private val config: ApiConfig,
    private val tokens: TokenStorage,
) {
    private val _messages = MutableSharedFlow<StompMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<StompMessage> = _messages.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val wsUrl: String
        get() = config.baseUrl.trimEnd('/').replaceFirst("http", "ws") + "/ws"

    suspend fun run(destinations: List<String>, refreshAuth: suspend () -> Unit) {
        var backoff = 1_000L
        while (currentCoroutineContext().isActive) {
            try {
                val token = tokens.load()?.accessToken ?: return
                session(token, destinations) { backoff = 1_000L }
            } catch (e: CancellationException) {
                throw e
            } catch (e: StompRejected) {
                runCatching { refreshAuth() }
            } catch (_: Exception) {
                // 네트워크 끊김·서버 재시작 → 아래에서 기다렸다가 다시 연결
            }
            _connected.value = false
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }

    private suspend fun session(token: String, destinations: List<String>, onConnected: () -> Unit) {
        // 공통 HttpTimeout(요청 30초)이 오래 열려 있는 웹소켓을 끊지 않도록 이 연결만 무제한
        client.webSocket(urlString = wsUrl, request = { timeout { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS } }) {
            send(Frame.Text(encode("CONNECT", "accept-version" to "1.2", "heart-beat" to "0,0", "Authorization" to "Bearer $token")))
            val first = readFrames(incoming.receive()).firstOrNull()
            if (first?.command != "CONNECTED") throw StompRejected(first?.headers?.get("message"))

            val byId = destinations.mapIndexed { i, dest -> "sub-$i" to dest }.toMap()
            byId.forEach { (id, dest) -> send(Frame.Text(encode("SUBSCRIBE", "id" to id, "destination" to dest))) }
            _connected.value = true
            onConnected()

            // 프록시(nginx 등)가 조용한 연결을 끊지 않도록 20초마다 빈 줄(STOMP heart-beat) 전송
            val keepAlive = launch {
                while (isActive) {
                    delay(20_000)
                    send(Frame.Text("\n"))
                }
            }
            try {
                for (frame in incoming) {
                    for (stomp in readFrames(frame)) {
                        when (stomp.command) {
                            "MESSAGE" -> {
                                val dest = byId[stomp.headers["subscription"]] ?: stomp.headers["destination"].orEmpty()
                                _messages.emit(StompMessage(dest, stomp.body))
                            }
                            "ERROR" -> throw IllegalStateException(stomp.headers["message"] ?: "STOMP ERROR")
                        }
                    }
                }
            } finally {
                keepAlive.cancel()
            }
        }
    }

    private class StompRejected(message: String?) : Exception(message)

    private data class StompFrame(val command: String, val headers: Map<String, String>, val body: String)

    private fun encode(command: String, vararg headers: Pair<String, String>, body: String = ""): String = buildString {
        append(command).append('\n')
        headers.forEach { (k, v) -> append(k).append(':').append(v).append('\n') }
        append('\n').append(body).append('\u0000')
    }

    /** 웹소켓 메시지 하나에 STOMP 프레임이 여러 개 또는 heart-beat(빈 줄)만 올 수 있다 */
    private fun readFrames(frame: Frame): List<StompFrame> {
        val text = (frame as? Frame.Text)?.readText() ?: return emptyList()
        return text.split('\u0000').mapNotNull { raw ->
            val chunk = raw.trimStart('\n', '\r')
            if (chunk.isEmpty()) return@mapNotNull null
            val headerEnd = chunk.indexOf("\n\n").takeIf { it >= 0 } ?: chunk.length
            val lines = chunk.substring(0, headerEnd).split('\n').map { it.trimEnd('\r') }
            val headers = lines.drop(1).mapNotNull { line ->
                val i = line.indexOf(':')
                if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
            }.toMap()
            val body = if (headerEnd + 2 <= chunk.length) chunk.substring(headerEnd + 2) else ""
            StompFrame(lines.first(), headers, body)
        }
    }
}
