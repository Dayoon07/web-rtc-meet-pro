package com.webrtcmeetpro.infrastructure.realtime

import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 사용자별 큐로 보낼 실시간 이벤트. 트랜잭션이 커밋된 뒤에만 실제로 전송된다 */
data class UserPush(val userIds: Collection<Long>, val destination: String, val payload: Any)

/**
 * 채팅 메시지, 알림 등을 접속 중인 사용자에게 STOMP 로 밀어준다.
 * 클라이언트는 /user/queue/chat, /user/queue/notifications 만 구독하므로
 * 다른 사람의 방/알림을 구독하는 일이 구조적으로 불가능하다.
 */
@Component
class RealtimePublisher(private val events: ApplicationEventPublisher) {

    fun push(userIds: Collection<Long>, destination: String, payload: Any) {
        if (userIds.isNotEmpty()) events.publishEvent(UserPush(userIds.toSet(), destination, payload))
    }

    companion object {
        const val CHAT = "/queue/chat"
        const val NOTIFICATIONS = "/queue/notifications"
        /** 회의실 안 상태 (녹화 시작·끝) */
        const val MEETING = "/queue/meeting"
        /** 접속 상태 (같은 워크스페이스 사람의 접속·자리 비움·나감) */
        const val PRESENCE = "/queue/presence"
    }
}

@Component
class UserPushSender(private val messaging: SimpMessagingTemplate) {

    // fallbackExecution: 트랜잭션 밖에서 발행돼도 바로 보낸다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun send(push: UserPush) {
        push.userIds.forEach { messaging.convertAndSendToUser(it.toString(), push.destination, push.payload) }
    }
}
