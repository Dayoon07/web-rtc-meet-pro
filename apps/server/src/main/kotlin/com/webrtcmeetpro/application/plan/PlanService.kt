package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.domain.plan.Payment
import com.webrtcmeetpro.domain.plan.PaymentRepository
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
import java.util.UUID

data class PlanView(
    val code: String,
    val name: String,
    val description: String?,
    val monthlyPrice: Long,
    val maxOwnedTeams: Int,
    val maxMembersPerTeam: Int,
    val maxGroupRoomsPerTeam: Int,
    val recordingEnabled: Boolean,
) {
    companion object {
        fun from(plan: Plan) = PlanView(
            code = plan.code,
            name = plan.name,
            description = plan.description,
            monthlyPrice = plan.monthlyPrice,
            maxOwnedTeams = plan.maxOwnedTeams,
            maxMembersPerTeam = plan.maxMembersPerTeam,
            maxGroupRoomsPerTeam = plan.maxGroupRoomsPerTeam,
            recordingEnabled = plan.recordingEnabled,
        )
    }
}

data class SubscriptionView(
    val plan: PlanView,
    val status: SubscriptionStatus,
    val startedAt: LocalDateTime,
    val currentPeriodEnd: LocalDateTime?,
    val ownedTeams: Long,
)

@Service
@Transactional(readOnly = true)
class PlanService(
    private val planRepository: PlanRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentRepository: PaymentRepository,
    private val teamRepository: TeamRepository,
) {

    fun listPlans(): List<PlanView> =
        planRepository.findAllByActiveTrueOrderBySortOrderAsc().map(PlanView::from)

    /** 팀/채널/초대 한도 체크에 쓰는 현재 플랜. 구독이 없으면 FREE */
    fun currentPlanOf(userId: Long): Plan {
        val active = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
        return active?.let { planRepository.findPlanById(it.planId) } ?: freePlan()
    }

    fun mySubscription(userId: Long): SubscriptionView {
        val subscription = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
            ?: throw BusinessException(ErrorCode.PLAN_NOT_FOUND)
        val plan = planRepository.findPlanById(subscription.planId)
            ?: throw BusinessException(ErrorCode.PLAN_NOT_FOUND)
        return SubscriptionView(
            plan = PlanView.from(plan),
            status = subscription.status,
            startedAt = subscription.startedAt,
            currentPeriodEnd = subscription.currentPeriodEnd,
            ownedTeams = teamRepository.countByOwnerIdAndStatus(userId, TeamStatus.ACTIVE),
        )
    }

    /** 회원가입 직후 FREE 구독 생성 */
    @Transactional
    fun startFreeSubscription(userId: Long): Subscription =
        subscriptionRepository.save(Subscription(userId = userId, planId = freePlan().id!!))

    /**
     * 플랜 변경. 현재 플랜들이 전부 0원이라 PG 없이 바로 바꾸고 0원 결제 기록을 남긴다.
     * 유료 플랜은 토스페이먼츠 승인 흐름(TossPaymentService, 다음 단계)을 거쳐야 한다.
     */
    @Transactional
    fun changePlan(userId: Long, planCode: String): SubscriptionView {
        val target = planRepository.findByCode(planCode)?.takeIf { it.active }
            ?: throw BusinessException(ErrorCode.PLAN_NOT_FOUND)
        if (!target.isFree) {
            throw BusinessException(ErrorCode.PAID_PLAN_REQUIRES_PAYMENT)
        }

        val current = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
        if (current?.planId == target.id) return mySubscription(userId)

        val ownedTeams = teamRepository.countByOwnerIdAndStatus(userId, TeamStatus.ACTIVE)
        if (ownedTeams > target.maxOwnedTeams) {
            throw BusinessException(
                ErrorCode.PLAN_DOWNGRADE_BLOCKED,
                mapOf("owned" to ownedTeams, "max" to target.maxOwnedTeams),
            )
        }

        current?.let {
            it.cancel()
            subscriptionRepository.save(it)
            // 사용자당 ACTIVE 1건 유니크 인덱스 때문에 새 구독 INSERT 전에 먼저 반영
            subscriptionRepository.flush()
        }
        val next = subscriptionRepository.save(Subscription(userId = userId, planId = target.id!!))

        val payment = Payment(
            userId = userId,
            planId = target.id!!,
            orderId = "ORD-" + UUID.randomUUID().toString().replace("-", ""),
            orderName = "${target.name} 플랜",
            amount = 0,
        )
        payment.approve(paymentKey = null, method = "FREE", receiptUrl = null)
        payment.linkSubscription(next.id!!)
        paymentRepository.save(payment)

        return mySubscription(userId)
    }

    private fun freePlan(): Plan =
        planRepository.findByCode(Plan.FREE_CODE)
            ?: throw IllegalStateException("plans 테이블에 FREE 플랜이 없습니다. db/seed.sql 을 실행하세요.")
}
