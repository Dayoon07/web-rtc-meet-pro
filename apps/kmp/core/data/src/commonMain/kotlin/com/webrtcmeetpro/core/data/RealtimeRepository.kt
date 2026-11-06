package com.webrtcmeetpro.core.data

import com.webrtcmeetpro.core.model.AppNotification
import com.webrtcmeetpro.core.model.ChatEvent
import com.webrtcmeetpro.core.network.ApiJson
import com.webrtcmeetpro.core.network.LimeetApi
import com.webrtcmeetpro.core.network.StompClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull

/**
 * 서버 실시간 푸시(STOMP). 앱 전체에서 연결 하나만 쓰고, 화면들은 필요한 이벤트만 골라 구독한다.
 * 로그인 동안 AppViewModel 이 run() 을 돌리고, 로그아웃하면 그 코루틴을 취소해 끊는다.
 */
class RealtimeRepository(
    private val stomp: StompClient,
    private val api: LimeetApi,
) {
    val connected: StateFlow<Boolean> = stomp.connected

    val chatEvents: Flow<ChatEvent> = stomp.messages
        .filter { it.destination == CHAT }
        .mapNotNull { runCatching { ApiJson.decodeFromString<ChatEvent>(it.body) }.getOrNull() }

    val notifications: Flow<AppNotification> = stomp.messages
        .filter { it.destination == NOTIFICATIONS }
        .mapNotNull { runCatching { ApiJson.decodeFromString<AppNotification>(it.body) }.getOrNull() }

    suspend fun run() = stomp.run(listOf(CHAT, NOTIFICATIONS)) {
        // 아무 인증 API 나 부르면 401 → Ktor 가 토큰을 회전해 저장한다
        api.chatUnreadCount()
    }

    private companion object {
        const val CHAT = "/user/queue/chat"
        const val NOTIFICATIONS = "/user/queue/notifications"
    }
}
