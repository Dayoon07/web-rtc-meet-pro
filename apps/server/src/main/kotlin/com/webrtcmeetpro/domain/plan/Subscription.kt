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
 * ACTIVE: 기간 안. PAST_DUE: 기간이 끝났고 자동결제가 실패해서 다시 시도하는 중 (그동안은 기능을 계속 쓸 수 있다).
 * EXPIRED: 기간이 끝남 (자동결제 꺼짐, 또는 자동결제가 끝내 실패). CANCELED: 예전 방식(플랜 바꾸기)으로 끝난 구독.
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
 * 워크스페이스(팀) 구독. [userId] = 결제한 사람, [teamId] = 요금제가 적용되는 워크스페이스.
 * 한 워크스페이스가 플랜마다 하나씩 여러 구독을 동시에 가질 수 있고(예: Team 과 Pro 를 둘 다 결제),
 * 기간이 남은 것 중 가장 높은 플랜을 쓴다. Team 이 끝나면 남은 Pro 로, Pro 도 끝나면 무료로.
 * teamId 가 없는 FREE 구독은 회원가입 때 만드는 기간 없는 구독이다 (어디에도 적용 안 됨).
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

    val teamId: Long? = null,
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

    /** 지금 이 구독의 플랜을 쓸 수 있는지 */
    fun isUsable(now: LocalDateTime = LocalDateTime.now()): Boolean = when (status) {
        SubscriptionStatus.ACTIVE -> currentPeriodEnd?.isAfter(now) ?: true
        SubscriptionStatus.PAST_DUE -> true
        else -> false
    }

    val isLive: Boolean get() = status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.PAST_DUE

    /** 결제 성공: 기간을 한 주기 늘린다. 아직 기간이 남았으면 남은 기간 뒤에 이어 붙인다 */
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

    /** 자동결제 실패: 하루 뒤 다시 시도 */
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

    /** "플랜이 만료됐어요" 창을 닫았다 */
    fun acknowledgeExpiry(now: LocalDateTime = LocalDateTime.now()) {
        expiryAcknowledgedAt = now
    }

    fun cancel(at: LocalDateTime = LocalDateTime.now()) {
        status = SubscriptionStatus.CANCELED
        canceledAt = at
        autoRenew = false
    }

    companion object {
        /** 자동결제가 이 횟수만큼 실패하면 만료 */
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
    /** 기간이 곧 끝나거나 끝난 구독 (스케줄러) */
    fun findAllByStatusInAndCurrentPeriodEndBefore(statuses: Collection<SubscriptionStatus>, before: LocalDateTime): List<Subscription>
}
