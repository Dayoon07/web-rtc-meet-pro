package com.webrtcmeetpro.application.notification

import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.infrastructure.realtime.RealtimePublisher
import com.webrtcmeetpro.domain.notification.Notification
import com.webrtcmeetpro.domain.notification.NotificationRepository
import com.webrtcmeetpro.domain.notification.NotificationType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

data class NotificationView(
    val id: Long,
    val type: NotificationType,
    val params: Map<String, Any?>,
    val link: String?,
    val read: Boolean,
    val createdAt: LocalDateTime,
)

@Service
@Transactional(readOnly = true)
class NotificationService(
    private val repository: NotificationRepository,
    private val realtime: RealtimePublisher,
    private val json: JsonMapper,
) {
    /** 여러 사용자에게 같은 알림. 저장 후(커밋 뒤) 접속 중인 사용자에게 실시간 전송 */
    @Transactional
    fun notify(userIds: Collection<Long>, type: NotificationType, params: Map<String, Any?>, link: String?) {
        val raw = json.writeValueAsString(params)
        userIds.toSet().forEach { userId ->
            val saved = repository.save(Notification(userId, type, raw, link))
            realtime.push(listOf(userId), RealtimePublisher.NOTIFICATIONS, toView(saved))
        }
    }

    fun recent(userId: Long): List<NotificationView> = repository.findTop30ByUserIdOrderByIdDesc(userId).map(::toView)

    fun unreadCount(userId: Long): Long = repository.countByUserIdAndReadAtIsNull(userId)

    @Transactional
    fun markRead(userId: Long, notificationId: Long) {
        val n = repository.findNotificationById(notificationId)?.takeIf { it.userId == userId }
            ?: throw BusinessException(ErrorCode.NOT_FOUND)
        n.markRead()
    }

    @Transactional
    fun markAllRead(userId: Long) {
        val now = LocalDateTime.now()
        repository.findAllByUserIdAndReadAtIsNull(userId).forEach { it.markRead(now) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun toView(n: Notification) = NotificationView(
        id = n.id!!,
        type = n.type,
        params = n.params?.let { json.readValue(it, Map::class.java) as Map<String, Any?> } ?: emptyMap(),
        link = n.link,
        read = n.readAt != null,
        createdAt = n.createdDt,
    )
}
