package com.webrtcmeetpro.domain.plan

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.YesNoConverter
import java.time.LocalDateTime

enum class SubscriptionStatus { ACTIVE, CANCELED, EXPIRED, PAST_DUE }

/** 사용자 구독 이력. 사용자당 ACTIVE 는 하나 (DB 함수 기반 유니크 인덱스로 보장) */
@Entity
@Table(name = "subscriptions")
class Subscription(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val planId: Long,

    @Column(nullable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),

    var currentPeriodEnd: LocalDateTime? = null,

    @Convert(converter = YesNoConverter::class)
    @Column(nullable = false)
    var autoRenew: Boolean = false,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SubscriptionStatus = SubscriptionStatus.ACTIVE
        protected set

    var canceledAt: LocalDateTime? = null
        protected set

    fun cancel(at: LocalDateTime = LocalDateTime.now()) {
        status = SubscriptionStatus.CANCELED
        canceledAt = at
        autoRenew = false
    }

    fun expire() {
        status = SubscriptionStatus.EXPIRED
    }
}

interface SubscriptionRepository {
    fun save(subscription: Subscription): Subscription
    fun flush()
    fun findByUserIdAndStatus(userId: Long, status: SubscriptionStatus): Subscription?
    fun findAllByUserIdOrderByStartedAtDesc(userId: Long): List<Subscription>
}
