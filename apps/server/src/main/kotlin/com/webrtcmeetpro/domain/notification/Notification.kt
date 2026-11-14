package com.webrtcmeetpro.domain.notification

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class NotificationType {
    TEAM_INVITATION,
    TEAM_INVITATION_ACCEPTED,
    CHAT_ROOM_INVITED,
    /** @모두 포함 */
    CHAT_MENTION,
    /** 내가 시작했거나 답글을 단 스레드 */
    THREAD_REPLY,
    MEETING_SCHEDULED,
    MEETING_REMINDER,
    MEETING_STARTED,
    MEETING_CANCELED,
    MEETING_UPDATED,
    MEETING_NOTES_READY,
    /** 자동결제가 꺼진 플랜이 3일 안에 끝남 */
    PLAN_EXPIRING,
    PLAN_EXPIRED,
    PAYMENT_FAILED,
    MEMBER_OVERAGE,
    OVERAGE_CHARGE_SOON,
    PAYMENT_REFUNDED,
}

/** 사용자 언어로 보여 주려고 문구는 저장하지 않고 type + params 만 저장한다 */
@Entity
@Table(name = "notifications")
class Notification(
    @Column(nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: NotificationType,

    @Column(length = 2000)
    val params: String?,

    val link: String?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    var id: Long? = null
        protected set

    var readAt: LocalDateTime? = null
        protected set

    fun markRead(at: LocalDateTime = LocalDateTime.now()) {
        if (readAt == null) readAt = at
    }
}

interface NotificationRepository {
    fun save(notification: Notification): Notification
    fun findNotificationById(id: Long): Notification?
    fun findTop30ByUserIdOrderByIdDesc(userId: Long): List<Notification>
    fun findAllByUserIdAndReadAtIsNull(userId: Long): List<Notification>
    fun countByUserIdAndReadAtIsNull(userId: Long): Long
}
