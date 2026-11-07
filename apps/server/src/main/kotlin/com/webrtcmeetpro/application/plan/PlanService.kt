package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.properties.TossProperties
import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.BillingKey
import com.webrtcmeetpro.domain.plan.BillingKeyRepository
import com.webrtcmeetpro.domain.plan.Plan
import com.webrtcmeetpro.domain.plan.PlanRepository
import com.webrtcmeetpro.domain.plan.Subscription
import com.webrtcmeetpro.domain.plan.SubscriptionRepository
import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class PlanView(
    val code: String,
    val name: String,
    val description: String?,
    val monthlyPrice: Long,
    /** 연 결제 금액 (월 가격 × 12 × 0.9) */
    val yearlyPrice: Long,
    val maxOwnedTeams: Int,
    val maxMembersPerTeam: Int,
    /** null = 제한 없음 */
    val maxGroupRoomsPerTeam: Int?,
    val maxMeetingParticipants: Int,
    val recordingEnabled: Boolean,
    val recordingStorageGb: Int,
    val recordingRetentionDays: Int,
    /** 클수록 높은 플랜 */
    val rank: Int,
) {
    companion object {
        fun from(plan: Plan) = PlanView(
            code = plan.code,
            name = plan.name,
            description = plan.description,
            monthlyPrice = plan.monthlyPrice,
            yearlyPrice = plan.price(BillingCycle.YEARLY),
            maxOwnedTeams = plan.maxOwnedTeams,
            maxMembersPerTeam = plan.maxMembersPerTeam,
            maxGroupRoomsPerTeam = plan.maxGroupRoomsPerTeam,
            maxMeetingParticipants = plan.maxMeetingParticipants,
            recordingEnabled = plan.recordingEnabled,
            recordingStorageGb = plan.recordingStorageGb,
            recordingRetentionDays = plan.recordingRetentionDays,
            rank = plan.sortOrder,
        )
    }
}

/** 유료 구독 하나 (기간이 남았거나 자동결제를 다시 시도 중인 것) */
data class SubscriptionItemView(
    val id: Long,
    val plan: PlanView,
    val status: SubscriptionStatus,
    val billingCycle: BillingCycle?,
    val autoRenew: Boolean,
    val startedAt: LocalDateTime,
    val currentPeriodEnd: LocalDateTime?,
    /** 지금 적용 중인 구독인지 (여러 개면 가장 높은 플랜 하나) */
    val inUse: Boolean,
    val failedAttempts: Int,
    val nextRetryAt: LocalDateTime?,
)

/** 아직 사용자가 확인하지 않은 만료 → 웹·앱이 "플랜이 만료됐어요" 창을 띄운다 */
data class ExpiryNoticeView(val subscriptionId: Long, val plan: PlanView, val expiredAt: LocalDateTime?)

data class CardView(val company: String?, val numberMasked: String?, val registeredAt: LocalDateTime)

data class SubscriptionView(
    /** 지금 적용 중인 플랜 (유료 구독이 없으면 FREE) */
    val plan: PlanView,
    val status: SubscriptionStatus,
    val startedAt: LocalDateTime?,
    /** 적용 중인 구독의 기간 끝 (FREE 는 null) */
    val currentPeriodEnd: LocalDateTime?,
    val ownedTeams: Long,
    val subscriptions: List<SubscriptionItemView>,
    val expiryNotice: ExpiryNoticeView?,
    /** 자동결제에 등록한 카드 */
    val card: CardView?,
    /** 토스 키가 설정돼 있어서 결제할 수 있는지 */
    val paymentsEnabled: Boolean,
)

/**
 * 요금제와 구독.
 * - 플랜마다 구독을 따로 가질 수 있고, 기간이 남은 것 중 가장 높은 플랜(sort_order)을 쓴다. 없으면 FREE.
 * - 팀·채팅방·초대·회의 인원·녹화 한도는 팀 "소유자" 의 지금 플랜으로 판단한다.
 * - 결제는 BillingService(토스 자동결제) 가 하고, 결제가 성공하면 [applyPurchase] 로 기간을 늘린다.
 */
@Service
@Transactional(readOnly = true)
class PlanService(
    private val planRepository: PlanRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val teamRepository: TeamRepository,
    private val billingKeyRepository: BillingKeyRepository,
    private val tossProperties: TossProperties,
) {

    fun listPlans(): List<PlanView> =
        planRepository.findAllByActiveTrueOrderBySortOrderAsc().map(PlanView::from)

    fun findPlan(code: String): Plan =
        planRepository.findByCode(code)?.takeIf { it.active } ?: throw BusinessException(ErrorCode.PLAN_NOT_FOUND)

    /** 지금 쓸 수 있는 플랜 (한도 체크용). 유료 구독이 다 끝났으면 FREE */
    fun currentPlanOf(userId: Long, now: LocalDateTime = LocalDateTime.now()): Plan =
        inUse(userId, now)?.second ?: freePlan()

    fun mySubscription(userId: Long, now: LocalDateTime = LocalDateTime.now()): SubscriptionView {
        val live = liveSubscriptions(userId)
        val current = inUse(userId, now, live)
        val plans = mutableMapOf<Long, Plan>()
        fun planOf(id: Long) = plans.getOrPut(id) { planRepository.findPlanById(id) ?: freePlan() }

        val paid = live.filter { it.currentPeriodEnd != null }
            .sortedWith(compareByDescending<Subscription> { planOf(it.planId).sortOrder }.thenBy { it.currentPeriodEnd })
            .map {
                SubscriptionItemView(
                    id = it.id!!,
                    plan = PlanView.from(planOf(it.planId)),
                    status = it.status,
                    billingCycle = it.billingCycle,
                    autoRenew = it.autoRenew,
                    startedAt = it.startedAt,
                    currentPeriodEnd = it.currentPeriodEnd,
                    inUse = it.id == current?.first?.id,
                    failedAttempts = it.failedAttempts,
                    nextRetryAt = it.nextRetryAt,
                )
            }
        val notice = subscriptionRepository.findAllByUserIdAndStatusIn(userId, listOf(SubscriptionStatus.EXPIRED))
            .filter { it.expiryAcknowledgedAt == null && it.expiredAt?.isAfter(now.minusDays(NOTICE_DAYS)) == true }
            .maxByOrNull { it.expiredAt!! }
            ?.let { ExpiryNoticeView(it.id!!, PlanView.from(planOf(it.planId)), it.expiredAt) }

        return SubscriptionView(
            plan = PlanView.from(current?.second ?: freePlan()),
            status = current?.first?.status ?: SubscriptionStatus.ACTIVE,
            startedAt = current?.first?.startedAt,
            currentPeriodEnd = current?.first?.currentPeriodEnd,
            ownedTeams = teamRepository.countByOwnerIdAndStatus(userId, TeamStatus.ACTIVE),
            subscriptions = paid,
            expiryNotice = notice,
            card = billingKeyRepository.findByUserIdAndActiveTrue(userId)?.let(::cardView),
            paymentsEnabled = tossProperties.enabled,
        )
    }

    /** 회원가입 직후 FREE 구독 생성 (기간 없음) */
    @Transactional
    fun startFreeSubscription(userId: Long): Subscription =
        subscriptionRepository.save(Subscription(userId = userId, planId = freePlan().id!!))

    /**
     * 결제가 성공한 뒤 구독 기간을 늘린다. 같은 플랜 구독이 살아 있으면 그 뒤에 이어 붙이고, 없으면 오늘부터 새 구독.
     * 다른 플랜 구독은 건드리지 않는다 (높은 플랜이 끝나면 남은 낮은 플랜으로 돌아간다).
     */
    @Transactional
    fun applyPurchase(userId: Long, plan: Plan, cycle: BillingCycle, autoRenew: Boolean, now: LocalDateTime = LocalDateTime.now()): Subscription {
        val live = liveSubscriptions(userId)
        val subscription = live.firstOrNull { it.planId == plan.id && it.currentPeriodEnd != null }
            ?: Subscription(userId = userId, planId = plan.id!!, startedAt = now, autoRenew = autoRenew, billingCycle = cycle)
        subscription.extend(cycle, now)
        subscription.autoRenew = autoRenew
        // 같은 플랜을 다시 결제했으면 그 플랜의 만료 안내는 필요 없다
        subscriptionRepository.findAllByUserIdAndStatusIn(userId, listOf(SubscriptionStatus.EXPIRED))
            .filter { it.planId == plan.id && it.expiryAcknowledgedAt == null }
            .forEach { it.acknowledgeExpiry(now) }
        return subscriptionRepository.save(subscription)
    }

    /** 자동결제 켜기·끄기. 끄면 기간이 끝날 때 결제하지 않고 만료된다. 켜려면 카드가 있어야 한다 */
    @Transactional
    fun setAutoRenew(userId: Long, subscriptionId: Long, autoRenew: Boolean): SubscriptionView {
        val subscription = ownedLive(userId, subscriptionId)
        if (autoRenew && billingKeyRepository.findByUserIdAndActiveTrue(userId) == null) {
            throw BusinessException(ErrorCode.BILLING_CARD_REQUIRED)
        }
        subscription.autoRenew = autoRenew
        return mySubscription(userId)
    }

    /** "플랜이 만료됐어요" 창을 닫음 */
    @Transactional
    fun acknowledgeExpiry(userId: Long, subscriptionId: Long) {
        val subscription = subscriptionRepository.findSubscriptionById(subscriptionId)?.takeIf { it.userId == userId }
            ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        subscription.acknowledgeExpiry()
    }

    fun planById(id: Long): Plan = planRepository.findPlanById(id) ?: freePlan()

    fun cardView(key: BillingKey) = CardView(key.cardCompany, key.cardNumberMasked, key.createdDt)

    // ---------------------------------------------------------------

    private fun liveSubscriptions(userId: Long) =
        subscriptionRepository.findAllByUserIdAndStatusIn(userId, listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE))

    /** 지금 적용 중인 (구독, 플랜). 쓸 수 있는 구독 중 가장 높은 플랜 */
    private fun inUse(userId: Long, now: LocalDateTime, live: List<Subscription> = liveSubscriptions(userId)): Pair<Subscription, Plan>? =
        live.filter { it.isUsable(now) }
            .mapNotNull { s -> planRepository.findPlanById(s.planId)?.let { s to it } }
            .maxWithOrNull(compareBy<Pair<Subscription, Plan>> { it.second.sortOrder }.thenBy { it.first.currentPeriodEnd ?: LocalDateTime.MAX })

    private fun ownedLive(userId: Long, subscriptionId: Long): Subscription =
        subscriptionRepository.findSubscriptionById(subscriptionId)
            ?.takeIf { it.userId == userId && it.isLive && it.currentPeriodEnd != null }
            ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)

    private fun freePlan(): Plan =
        planRepository.findByCode(Plan.FREE_CODE)
            ?: throw IllegalStateException("plans 테이블에 FREE 플랜이 없습니다. db/seed.sql 을 실행하세요.")

    companion object {
        /** 이 기간 안에 끝난 구독만 만료 창으로 알린다 */
        const val NOTICE_DAYS = 30L
    }
}
