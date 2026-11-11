package com.webrtcmeetpro.domain.push

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 웹 푸시 구독: 브라우저(기기)마다 하나. 같은 브라우저가 다시 구독하면 같은 endpoint 의 행을 고친다 */
@Entity
@Table(name = "push_subscriptions")
class PushSubscription(
    @Column(nullable = false)
    var userId: Long,

    @Column(nullable = false, unique = true)
    val endpoint: String,

    @Column(nullable = false)
    var p256dh: String,

    @Column(name = "auth_secret", nullable = false)
    var authSecret: String,

    var lang: String?,

    var userAgent: String?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    var id: Long? = null
        protected set

    var lastSentAt: LocalDateTime? = null
        protected set

    /** 같은 브라우저로 다른 사람이 로그인하면 그 사람 것으로 바꾼다 */
    fun renew(userId: Long, p256dh: String, authSecret: String, lang: String?, userAgent: String?) {
        this.userId = userId
        this.p256dh = p256dh
        this.authSecret = authSecret
        this.lang = lang
        this.userAgent = userAgent
        deleted = false
    }

    fun sent(at: LocalDateTime = LocalDateTime.now()) {
        lastSentAt = at
    }

    /** 끄기 · 브라우저에서 사라진 구독 */
    fun cancel() {
        deleted = true
    }
}

interface PushSubscriptionRepository {
    fun save(subscription: PushSubscription): PushSubscription
    fun findByEndpoint(endpoint: String): PushSubscription?
    fun findAllByUserIdInAndDeletedFalse(userIds: Collection<Long>): List<PushSubscription>
}
