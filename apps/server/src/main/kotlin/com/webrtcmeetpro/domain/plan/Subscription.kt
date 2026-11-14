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

/**
 * PAST_DUE: 기간이 끝났지만 자동결제를 다시 시도하는 중이라 기능은 계속 쓸 수 있다.
 * CANCELED: 예전 방식(플랜 바꾸기)으로 끝난 구독이 DB 에 남아 있다.
 */
enum class SubscriptionStatus { ACTIVE, CANCELED, EXPIRED, PAST_DUE }

enum class BillingCycle {
    MONTHLY, YEARLY;

    fun addTo(from: LocalDateTime): LocalDateTime = when (this) {
        MONTHLY -> from.plusMonths(1)
        YEARLY -> from.plusYears(1)
    }
}

/**
 * userId 는 결제한 사람(팀 소유자), teamId 는 결제할 때 고른 팀이다. 플랜은 소유자가 만든 모든 팀에 적용된다.
 * teamId 가 없는 FREE 구독은 가입 때 만드는 기간 없는 구독이라 어디에도 적용되지 않는다.
 */
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

    @Enumerated(EnumType.STRING)
    var billingCycle: BillingCycle? = null,

    teamId: Long? = null,
) : BaseEntity() {

    @Column(name = "team_id")
    var teamId: Long? = teamId
        protected set

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

    @Column(nullable = false)
    var failedAttempts: Int = 0
        protected set

    var nextRetryAt: LocalDateTime? = null
        protected set

    var expiringNotifiedAt: LocalDateTime? = null
        protected set

    var expiredAt: LocalDateTime? = null
        protected set

    var expiryAcknowledgedAt: LocalDateTime? = null
        protected set

    fun isUsable(now: LocalDateTime = LocalDateTime.now()): Boolean = when (status) {
        SubscriptionStatus.ACTIVE -> currentPeriodEnd?.isAfter(now) ?: true
        SubscriptionStatus.PAST_DUE -> true
        else -> false
    }

    val isLive: Boolean get() = status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.PAST_DUE

    /** 아직 기간이 남았으면 남은 기간 뒤에 이어 붙인다 */
    fun extend(cycle: BillingCycle, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val from = currentPeriodEnd?.takeIf { it.isAfter(now) } ?: now
        currentPeriodEnd = cycle.addTo(from)
        billingCycle = cycle
        status = SubscriptionStatus.ACTIVE
        failedAttempts = 0
        nextRetryAt = null
        expiringNotifiedAt = null
        return currentPeriodEnd!!
    }

    fun paymentFailed(now: LocalDateTime = LocalDateTime.now()) {
        failedAttempts += 1
        status = SubscriptionStatus.PAST_DUE
        nextRetryAt = now.plusDays(1)
    }

    fun markExpiringNotified(now: LocalDateTime = LocalDateTime.now()) {
        expiringNotifiedAt = now
    }

    fun expire(now: LocalDateTime = LocalDateTime.now()) {
        status = SubscriptionStatus.EXPIRED
        expiredAt = now
        autoRenew = false
        nextRetryAt = null
    }

    fun acknowledgeExpiry(now: LocalDateTime = LocalDateTime.now()) {
        expiryAcknowledgedAt = now
    }

    fun moveTo(teamId: Long) {
        this.teamId = teamId
    }

    fun cancel(at: LocalDateTime = LocalDateTime.now()) {
        status = SubscriptionStatus.CANCELED
        canceledAt = at
        autoRenew = false
    }

    companion object {
        const val MAX_PAYMENT_ATTEMPTS = 3
    }
}

interface SubscriptionRepository {
    fun save(subscription: Subscription): Subscription
    fun flush()
    fun findSubscriptionById(id: Long): Subscription?
    fun findAllByUserIdAndStatusIn(userId: Long, statuses: Collection<SubscriptionStatus>): List<Subscription>
    fun findAllByTeamIdAndStatusIn(teamId: Long, statuses: Collection<SubscriptionStatus>): List<Subscription>
    fun findAllByTeamIdInAndStatusIn(teamIds: Collection<Long>, statuses: Collection<SubscriptionStatus>): List<Subscription>
    fun findAllByUserIdOrderByStartedAtDesc(userId: Long): List<Subscription>
    fun findAllByStatusInAndCurrentPeriodEndBefore(statuses: Collection<SubscriptionStatus>, before: LocalDateTime): List<Subscription>
}
