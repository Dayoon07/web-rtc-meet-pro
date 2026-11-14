package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.domain.plan.dto.res.CardResDto
import com.webrtcmeetpro.domain.plan.dto.res.ExpiryNoticeResDto
import com.webrtcmeetpro.domain.plan.dto.res.PlanResDto
import com.webrtcmeetpro.domain.plan.dto.res.SubscriptionItemResDto
import com.webrtcmeetpro.domain.plan.dto.res.SubscriptionResDto
import com.webrtcmeetpro.domain.plan.dto.res.WorkspaceRefResDto
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.properties.AiProperties
import com.webrtcmeetpro.config.properties.TossProperties
import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.BillingKey
import com.webrtcmeetpro.domain.plan.BillingKeyRepository
import com.webrtcmeetpro.domain.plan.Plan
import com.webrtcmeetpro.domain.plan.PlanRepository
import com.webrtcmeetpro.domain.plan.Subscription
import com.webrtcmeetpro.domain.plan.SubscriptionRepository
import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import com.webrtcmeetpro.domain.team.Team
import com.webrtcmeetpro.domain.team.TeamMemberRepository
import com.webrtcmeetpro.domain.team.TeamMemberStatus
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 플랜은 팀이 아니라 팀 소유자에게 붙는다: 한 번 결제하면 그 사람이 만든 모든 팀에 적용된다.
 * 여러 플랜을 동시에 가질 수 있고, 기간이 남은 것 중 가장 높은 플랜을 쓴다.
 */
@Service
@Transactional(readOnly = true)
class PlanService(
    private val planRepository: PlanRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val teamRepository: TeamRepository,
    private val teamMemberRepository: TeamMemberRepository,
    private val billingKeyRepository: BillingKeyRepository,
    private val tossProperties: TossProperties,
    private val aiProperties: AiProperties = AiProperties(),
) {

    /** 실시간 자막을 꺼 두었으면 자막 시간을 0 으로 보내 요금제 카드에서 빠지게 한다 */
    fun view(plan: Plan): PlanResDto =
        PlanResDto.from(plan).let { if (aiProperties.liveCaptions) it else it.copy(liveCaptionMinutes = 0) }

    fun listPlans(): List<PlanResDto> =
        planRepository.findAllByActiveTrueOrderBySortOrderAsc().map(::view)

    fun findPlan(code: String): Plan =
        planRepository.findByCode(code)?.takeIf { it.active } ?: throw BusinessException(ErrorCode.PLAN_NOT_FOUND)

    fun planOfTeam(teamId: Long, now: LocalDateTime = LocalDateTime.now()): Plan =
        teamRepository.findTeamById(teamId)?.let { planOfOwner(it.ownerId, now) } ?: freePlan()

    fun planOfOwner(ownerId: Long, now: LocalDateTime = LocalDateTime.now()): Plan =
        inUse(liveOfOwner(ownerId), now)?.second ?: freePlan()

    fun bestOwnedPlan(userId: Long, now: LocalDateTime = LocalDateTime.now()): Plan = planOfOwner(userId, now)

    fun ownedTeams(ownerId: Long): List<Team> =
        teamRepository.findAllByOwnerIdAndStatus(ownerId, TeamStatus.ACTIVE).sortedBy { it.id }

    /** AI 사용 시간처럼 소유자 단위로 합칠 때 */
    fun siblingTeamIds(teamId: Long): List<Long> {
        val team = teamRepository.findTeamById(teamId) ?: return listOf(teamId)
        return ownedTeams(team.ownerId).map { it.id!! }.ifEmpty { listOf(teamId) }
    }

    /** 여러 팀에 있어도 한 명으로 센다 */
    fun ownerMemberIds(ownerId: Long): Set<Long> {
        val teamIds = ownedTeams(ownerId).map { it.id!! }
        if (teamIds.isEmpty()) return emptySet()
        return teamMemberRepository.findAllByTeamIdInAndStatus(teamIds, TeamMemberStatus.ACTIVE).map { it.userId }.toSet()
    }

    fun ownerMemberCount(ownerId: Long): Long = ownerMemberIds(ownerId).size.toLong()

    fun workspaceSubscription(teamId: Long, viewerId: Long, now: LocalDateTime = LocalDateTime.now()): SubscriptionResDto {
        val team = requireMemberTeam(teamId, viewerId)
        val live = liveOfOwner(team.ownerId)
        val current = inUse(live, now)
        val plans = mutableMapOf<Long, Plan>()
        fun planOf(id: Long) = plans.getOrPut(id) { planById(id) }

        val items = live.filter { it.currentPeriodEnd != null }
            .sortedWith(compareByDescending<Subscription> { planOf(it.planId).sortOrder }.thenBy { it.currentPeriodEnd })
            .map {
                SubscriptionItemResDto(
                    id = it.id!!,
                    plan = view(planOf(it.planId)),
                    status = it.status,
                    billingCycle = it.billingCycle,
                    autoRenew = it.autoRenew,
                    startedAt = it.startedAt,
                    currentPeriodEnd = it.currentPeriodEnd,
                    inUse = it.id == current?.first?.id,
                    failedAttempts = it.failedAttempts,
                    nextRetryAt = it.nextRetryAt,
                    mine = it.userId == viewerId,
                )
            }
        return SubscriptionResDto(
            team = WorkspaceRefResDto(team.id!!, team.name),
            appliesTo = ownedTeams(team.ownerId).map { WorkspaceRefResDto(it.id!!, it.name) },
            plan = view(current?.second ?: freePlan()),
            status = current?.first?.status ?: SubscriptionStatus.ACTIVE,
            startedAt = current?.first?.startedAt,
            currentPeriodEnd = current?.first?.currentPeriodEnd,
            memberCount = ownerMemberCount(team.ownerId),
            subscriptions = items,
            canManage = team.ownerId == viewerId,
            card = billingKeyRepository.findByUserIdAndActiveTrue(viewerId)?.let(::cardView),
            paymentsEnabled = tossProperties.enabled,
        )
    }

    /** 결제한 플랜 중 만료일이 지난 플랜 (최근 30일, 아직 확인하지 않은 것) */
    fun expiryNotices(userId: Long, now: LocalDateTime = LocalDateTime.now()): List<ExpiryNoticeResDto> =
        subscriptionRepository.findAllByUserIdAndStatusIn(userId, listOf(SubscriptionStatus.EXPIRED))
            .filter { it.expiryAcknowledgedAt == null && it.expiredAt?.isAfter(now.minusDays(NOTICE_DAYS)) == true }
            .sortedByDescending { it.expiredAt }
            .map {
                val team = it.teamId?.let(teamRepository::findTeamById)
                ExpiryNoticeResDto(
                    subscriptionId = it.id!!,
                    plan = view(planById(it.planId)),
                    expiredAt = it.expiredAt,
                    teamId = team?.id,
                    teamName = team?.name,
                    currentPlan = view(team?.id?.let { id -> planOfTeam(id, now) } ?: freePlan()),
                )
            }

    /** 기간이 없어 어디에도 적용되지 않지만, 구독이 하나도 없는 사용자를 만들지 않으려고 둔다 */
    @Transactional
    fun startFreeSubscription(userId: Long): Subscription =
        subscriptionRepository.save(Subscription(userId = userId, planId = freePlan().id!!))

    /** 같은 플랜이 살아 있으면 그 뒤로 이어 붙인다. 다른 플랜은 건드리지 않아 높은 플랜이 끝나면 낮은 플랜으로 돌아간다 */
    @Transactional
    fun applyPurchase(
        teamId: Long,
        payerId: Long,
        plan: Plan,
        cycle: BillingCycle,
        autoRenew: Boolean,
        now: LocalDateTime = LocalDateTime.now(),
    ): Subscription {
        val subscription = liveOfOwner(payerId).firstOrNull { it.planId == plan.id && it.currentPeriodEnd != null }
            ?: Subscription(userId = payerId, planId = plan.id!!, startedAt = now, autoRenew = autoRenew, billingCycle = cycle, teamId = teamId)
        subscription.extend(cycle, now)
        subscription.autoRenew = autoRenew
        // 같은 플랜을 다시 샀으면 그 플랜의 만료 안내 창은 더 띄울 필요가 없다
        subscriptionRepository.findAllByUserIdAndStatusIn(payerId, listOf(SubscriptionStatus.EXPIRED))
            .filter { it.planId == plan.id && it.expiryAcknowledgedAt == null }
            .forEach { it.acknowledgeExpiry(now) }
        return subscriptionRepository.save(subscription)
    }

    @Transactional
    fun setAutoRenew(userId: Long, subscriptionId: Long, autoRenew: Boolean): SubscriptionResDto {
        val subscription = subscriptionRepository.findSubscriptionById(subscriptionId)
            ?.takeIf { it.userId == userId && it.isLive && it.currentPeriodEnd != null && it.teamId != null }
            ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        if (autoRenew && billingKeyRepository.findByUserIdAndActiveTrue(userId) == null) {
            throw BusinessException(ErrorCode.BILLING_CARD_REQUIRED)
        }
        subscription.autoRenew = autoRenew
        return workspaceSubscription(subscription.teamId!!, userId)
    }

    @Transactional
    fun acknowledgeExpiry(userId: Long, subscriptionId: Long) {
        val subscription = subscriptionRepository.findSubscriptionById(subscriptionId)?.takeIf { it.userId == userId }
            ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        subscription.acknowledgeExpiry()
    }

    /** 플랜은 소유자의 것이라 다른 팀이 남아 있으면 구독과 초과 설정을 그 팀으로 옮기고, 마지막 팀이면 구독을 끝낸다 */
    @Transactional
    fun onTeamDeleted(teamId: Long) {
        val team = teamRepository.findTeamById(teamId) ?: return
        val live = liveOf(teamId)
        val heir = ownedTeams(team.ownerId).firstOrNull { it.id != teamId }
        if (heir == null) {
            live.forEach { it.cancel() }
            return
        }
        live.forEach { it.moveTo(heir.id!!) }
        if (team.overageAllowed && !heir.overageAllowed) heir.takeOverOverage(team)
    }

    fun requireOwnedTeam(teamId: Long, userId: Long): Team {
        val team = teamRepository.findTeamById(teamId)?.takeIf { it.status == TeamStatus.ACTIVE } ?: throw BusinessException(ErrorCode.TEAM_NOT_FOUND)
        if (team.ownerId != userId) throw BusinessException(ErrorCode.NOT_TEAM_OWNER)
        return team
    }

    fun planById(id: Long): Plan = planRepository.findPlanById(id) ?: freePlan()

    fun inUseSubscription(teamId: Long, now: LocalDateTime = LocalDateTime.now()): Pair<Subscription, Plan>? =
        teamRepository.findTeamById(teamId)?.let { inUse(liveOfOwner(it.ownerId), now) }

    fun requireMemberTeamOf(teamId: Long, userId: Long): Team = requireMemberTeam(teamId, userId)

    fun cardView(key: BillingKey) = CardResDto(key.cardCompany, key.cardNumberMasked, key.createdDt)

    private fun liveOf(teamId: Long) = subscriptionRepository.findAllByTeamIdAndStatusIn(teamId, LIVE)

    private fun liveOfOwner(ownerId: Long): List<Subscription> {
        val teamIds = ownedTeams(ownerId).map { it.id!! }
        return if (teamIds.isEmpty()) emptyList() else subscriptionRepository.findAllByTeamIdInAndStatusIn(teamIds, LIVE)
    }

    private fun requireMemberTeam(teamId: Long, userId: Long): Team {
        val team = teamRepository.findTeamById(teamId)?.takeIf { it.status == TeamStatus.ACTIVE } ?: throw BusinessException(ErrorCode.TEAM_NOT_FOUND)
        val member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
        if (member?.status != TeamMemberStatus.ACTIVE) throw BusinessException(ErrorCode.NOT_TEAM_MEMBER)
        return team
    }

    private fun inUse(live: List<Subscription>, now: LocalDateTime): Pair<Subscription, Plan>? =
        live.filter { it.isUsable(now) && it.currentPeriodEnd != null }
            .mapNotNull { s -> planRepository.findPlanById(s.planId)?.let { s to it } }
            .maxWithOrNull(compareBy<Pair<Subscription, Plan>> { it.second.sortOrder }.thenBy { it.first.currentPeriodEnd })

    private fun freePlan(): Plan =
        planRepository.findByCode(Plan.FREE_CODE)
            ?: throw IllegalStateException("plans 테이블에 FREE 플랜이 없습니다. db/seed.sql 을 실행하세요.")

    companion object {
        const val NOTICE_DAYS = 30L
        private val LIVE = listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE)
    }
}
