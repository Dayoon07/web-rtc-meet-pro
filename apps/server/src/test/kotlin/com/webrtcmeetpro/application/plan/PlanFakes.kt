package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.config.properties.TossProperties
import com.webrtcmeetpro.domain.plan.BillingKey
import com.webrtcmeetpro.domain.plan.BillingKeyRepository
import com.webrtcmeetpro.domain.plan.Payment
import com.webrtcmeetpro.domain.plan.PaymentRepository
import com.webrtcmeetpro.domain.plan.Plan
import com.webrtcmeetpro.domain.plan.PlanRepository
import com.webrtcmeetpro.domain.plan.Subscription
import com.webrtcmeetpro.domain.plan.SubscriptionRepository
import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import com.webrtcmeetpro.domain.team.Team
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamStatus
import java.time.LocalDateTime

/** 테스트용: id 가 비어 있으면 채운다 (JPA 대신) */
object TestIds {
    private var seq = 0L
    fun <T : Any> assign(entity: T): T {
        var c: Class<*>? = entity.javaClass
        while (c != null) {
            c.declaredFields.firstOrNull { it.name == "id" }?.let {
                it.isAccessible = true
                if (it.get(entity) == null) it.set(entity, ++seq)
                return entity
            }
            c = c.superclass
        }
        return entity
    }
}

fun testPlan(code: String, price: Long, rank: Int, recording: Boolean = false, storageGb: Int = 0, participants: Int = 6) = TestIds.assign(
    Plan(
        code = code, name = code, description = null, monthlyPrice = price,
        maxOwnedTeams = 1 + rank, maxMembersPerTeam = 10 * (rank + 1), maxGroupRoomsPerTeam = if (rank >= 2) null else 3,
        maxMeetingParticipants = participants, recordingEnabled = recording, recordingStorageGb = storageGb, sortOrder = rank,
    ),
)

/** 요금제·구독·카드·결제 메모리 저장소 */
class PlanFakes(val plans: List<Plan>) {
    val subscriptions = mutableListOf<Subscription>()
    val billingKeys = mutableListOf<BillingKey>()
    val payments = mutableListOf<Payment>()

    val planRepo = object : PlanRepository {
        override fun findPlanById(id: Long) = plans.find { it.id == id }
        override fun findByCode(code: String) = plans.find { it.code == code }
        override fun findAllByActiveTrueOrderBySortOrderAsc() = plans.sortedBy { it.sortOrder }
    }
    val subscriptionRepo = object : SubscriptionRepository {
        override fun save(subscription: Subscription) = TestIds.assign(subscription).also { if (it !in subscriptions) subscriptions += it }
        override fun flush() {}
        override fun findSubscriptionById(id: Long) = subscriptions.find { it.id == id }
        override fun findAllByUserIdAndStatusIn(userId: Long, statuses: Collection<SubscriptionStatus>) =
            subscriptions.filter { it.userId == userId && it.status in statuses }
        override fun findAllByUserIdOrderByStartedAtDesc(userId: Long) = subscriptions.filter { it.userId == userId }.sortedByDescending { it.startedAt }
        override fun findAllByStatusInAndCurrentPeriodEndBefore(statuses: Collection<SubscriptionStatus>, before: LocalDateTime) =
            subscriptions.filter { it.status in statuses && it.currentPeriodEnd?.isBefore(before) == true }
    }
    val billingKeyRepo = object : BillingKeyRepository {
        override fun save(billingKey: BillingKey) = TestIds.assign(billingKey).also { if (it !in billingKeys) billingKeys += it }
        override fun findByUserIdAndActiveTrue(userId: Long) = billingKeys.find { it.userId == userId && it.active }
        override fun findAllByUserIdAndActiveTrue(userId: Long) = billingKeys.filter { it.userId == userId && it.active }
        override fun findBillingKeyById(id: Long) = billingKeys.find { it.id == id }
    }
    val paymentRepo = object : PaymentRepository {
        override fun save(payment: Payment) = TestIds.assign(payment).also { if (it !in payments) payments += it }
        override fun findByOrderId(orderId: String) = payments.find { it.orderId == orderId }
        override fun findAllByUserIdOrderByCreatedDtDesc(userId: Long) = payments.filter { it.userId == userId }
        override fun findTop50ByUserIdOrderByCreatedDtDesc(userId: Long) = payments.filter { it.userId == userId }.take(50)
    }
    val teamRepo = object : TeamRepository {
        override fun save(team: Team) = team
        override fun findTeamById(id: Long): Team? = null
        override fun countByOwnerIdAndStatus(ownerId: Long, status: TeamStatus) = 0L
        override fun findAllByIdInAndStatus(ids: Collection<Long>, status: TeamStatus) = emptyList<Team>()
    }

    fun planService(toss: TossProperties = TossProperties()) = PlanService(planRepo, subscriptionRepo, teamRepo, billingKeyRepo, toss)
}
