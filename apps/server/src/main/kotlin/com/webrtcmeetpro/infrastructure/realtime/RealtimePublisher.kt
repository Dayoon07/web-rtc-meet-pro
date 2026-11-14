package com.webrtcmeetpro.infrastructure.realtime

import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 롤백된 데이터를 보내지 않게 커밋된 뒤에만 전송한다 */
data class UserPush(val userIds: Collection<Long>, val destination: String, val payload: Any)

/** 클라이언트는 자기 큐만 구독하므로 다른 사람의 방·알림을 받는 일이 구조적으로 불가능하다 */
@Component
class RealtimePublisher(private val events: ApplicationEventPublisher) {

    fun push(userIds: Collection<Long>, destination: String, payload: Any) {
        if (userIds.isNotEmpty()) events.publishEvent(UserPush(userIds.toSet(), destination, payload))
    }

    companion object {
        const val CHAT = "/queue/chat"
        const val NOTIFICATIONS = "/queue/notifications"
        const val MEETING = "/queue/meeting"
        const val PRESENCE = "/queue/presence"
    }
}

@Component
class UserPushSender(private val messaging: SimpMessagingTemplate) {

    // 트랜잭션 밖에서 발행돼도 보낸다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun send(push: UserPush) {
        push.userIds.forEach { messaging.convertAndSendToUser(it.toString(), push.destination, push.payload) }
    }
}
