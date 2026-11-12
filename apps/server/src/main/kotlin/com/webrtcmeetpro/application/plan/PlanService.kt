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
import com.webrtcmeetpro.domain.team.Team
import com.webrtcmeetpro.domain.team.TeamMemberRepository
import com.webrtcmeetpro.domain.team.TeamMemberStatus
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
    val maxMembersPerTeam: Int,
    /** 워크스페이스당 채널 수. null = 제한 없음 */
    val maxGroupRoomsPerTeam: Int?,
    val maxMeetingParticipants: Int,
    val recordingEnabled: Boolean,
    val recordingStorageGb: Int,
    val recordingRetentionDays: Int,
    /** 한 달 AI 회의록 시간(분). 0 = 없음 */
    val aiNotesMinutes: Int,
    /** 한 달 실시간 자막·번역 시간(분). 0 = 없음 */
    val liveCaptionMinutes: Int,
    /** 멤버 한도를 넘은 1명당 한 달 요금 (소유자가 허용했을 때) */
    val overagePricePerMember: Long,
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
            maxMembersPerTeam = plan.maxMembersPerTeam,
            maxGroupRoomsPerTeam = plan.maxGroupRoomsPerTeam,
            maxMeetingParticipants = plan.maxMeetingParticipants,
            recordingEnabled = plan.recordingEnabled,
            recordingStorageGb = plan.recordingStorageGb,
            recordingRetentionDays = plan.recordingRetentionDays,
            aiNotesMinutes = plan.aiNotesMinutes,
            liveCaptionMinutes = plan.liveCaptionMinutes,
            overagePricePerMember = plan.overagePricePerMember,
            rank = plan.sortOrder,
        )
    }
}

/** 워크스페이스의 유료 구독 하나 (기간이 남았거나 자동결제를 다시 시도 중인 것) */
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
    /** 보는 사람이 결제한 구독인지 (자동결제를 켜고 끌 수 있는 사람) */
    val mine: Boolean,
)

/** 아직 확인하지 않은 만료 → 웹·앱이 "플랜이 만료됐어요" 창을 띄운다 */
data class ExpiryNoticeView(
    val subscriptionId: Long,
    val plan: PlanView,
    val expiredAt: LocalDateTime?,
    val teamId: Long?,
    val teamName: String?,
    /** 그 워크스페이스에 지금 적용 중인 플랜 */
    val currentPlan: PlanView,
)

data class CardView(val company: String?, val numberMasked: String?, val registeredAt: LocalDateTime)

data class WorkspaceRef(val id: Long, val name: String)

/** 요금제 상태 (플랜은 팀 소유자의 것이라 그 사람이 만든 모든 팀에 같다) */
data class SubscriptionView(
    val team: WorkspaceRef,
    /** 이 플랜이 적용되는 팀 = 소유자가 만든 모든 팀 */
    val appliesTo: List<WorkspaceRef>,
    /** 지금 적용 중인 플랜 (유료 구독이 없으면 FREE) */
    val plan: PlanView,
    val status: SubscriptionStatus,
    val startedAt: LocalDateTime?,
    /** 적용 중인 구독의 기간 끝 (FREE 는 null) */
    val currentPeriodEnd: LocalDateTime?,
    /** 소유자의 모든 팀 사람 수 (여러 팀에 있어도 한 명) */
    val memberCount: Long,
    val subscriptions: List<SubscriptionItemView>,
    /** 결제할 수 있는 사람 (워크스페이스 소유자) */
    val canManage: Boolean,
    /** 보는 사람이 등록한 카드 */
    val card: CardView?,
    /** 토스 키가 설정돼 있어서 결제할 수 있는지 */
    val paymentsEnabled: Boolean,
)

/**
 * 요금제와 구독. **플랜은 팀 소유자(회사 계정)에게** 붙는다: 한 번 결제하면 그 사람이 만든 모든 팀에 적용.
 * - 팀은 몇 개든 무료로 만들 수 있고, 처음엔 FREE.
 * - 한 소유자가 여러 플랜을 동시에 가질 수 있고, 기간이 남은 것 중 가장 높은 플랜을 쓴다.
 * - 멤버 한도는 소유자의 모든 팀 사람을 합쳐(겹치는 사람은 한 명) 센다. 회의 인원·녹화·팀당 채널 수도 소유자 플랜으로.
 * - 결제는 소유자가 하고(BillingService), 성공하면 [applyPurchase] 로 기간을 늘린다.
 * - 구독 행에는 결제할 때 고른 팀(teamId)이 남는다. 그 팀을 지우면 남은 팀으로 옮긴다 ([onTeamDeleted]).
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
) {

    fun listPlans(): List<PlanView> =
        planRepository.findAllByActiveTrueOrderBySortOrderAsc().map(PlanView::from)

    fun findPlan(code: String): Plan =
        planRepository.findByCode(code)?.takeIf { it.active } ?: throw BusinessException(ErrorCode.PLAN_NOT_FOUND)

    /** 팀에 지금 적용 중인 플랜 = 그 팀 소유자의 플랜 (한도 체크용). 유료 구독이 다 끝났으면 FREE */
    fun planOfTeam(teamId: Long, now: LocalDateTime = LocalDateTime.now()): Plan =
        teamRepository.findTeamById(teamId)?.let { planOfOwner(it.ownerId, now) } ?: freePlan()

    /** 소유자(회사 계정)의 플랜. 그 사람이 만든 모든 팀에 같은 플랜이 적용된다 */
    fun planOfOwner(ownerId: Long, now: LocalDateTime = LocalDateTime.now()): Plan =
        inUse(liveOfOwner(ownerId), now)?.second ?: freePlan()

    /** 내 플랜 (내 정보 화면 표시용) */
    fun bestOwnedPlan(userId: Long, now: LocalDateTime = LocalDateTime.now()): Plan = planOfOwner(userId, now)

    /** 소유자가 만든 팀 (만든 순) */
    fun ownedTeams(ownerId: Long): List<Team> =
        teamRepository.findAllByOwnerIdAndStatus(ownerId, TeamStatus.ACTIVE).sortedBy { it.id }

    /** 이 팀과 같은 소유자의 팀 id 전부 (AI 사용 시간처럼 소유자 단위로 합칠 때) */
    fun siblingTeamIds(teamId: Long): List<Long> {
        val team = teamRepository.findTeamById(teamId) ?: return listOf(teamId)
        return ownedTeams(team.ownerId).map { it.id!! }.ifEmpty { listOf(teamId) }
    }

    /** 소유자의 모든 팀 사람 (여러 팀에 있어도 한 명) — 멤버 한도·초과 요금 기준 */
    fun ownerMemberIds(ownerId: Long): Set<Long> {
        val teamIds = ownedTeams(ownerId).map { it.id!! }
        if (teamIds.isEmpty()) return emptySet()
        return teamMemberRepository.findAllByTeamIdInAndStatus(teamIds, TeamMemberStatus.ACTIVE).map { it.userId }.toSet()
    }

    fun ownerMemberCount(ownerId: Long): Long = ownerMemberIds(ownerId).size.toLong()

    /** 요금제 화면 (그 팀 소유자의 플랜). 멤버면 볼 수 있고, 결제는 소유자만 */
    fun workspaceSubscription(teamId: Long, viewerId: Long, now: LocalDateTime = LocalDateTime.now()): SubscriptionView {
        val team = requireMemberTeam(teamId, viewerId)
        val live = liveOfOwner(team.ownerId)
        val current = inUse(live, now)
        val plans = mutableMapOf<Long, Plan>()
        fun planOf(id: Long) = plans.getOrPut(id) { planById(id) }

        val items = live.filter { it.currentPeriodEnd != null }
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
                    mine = it.userId == viewerId,
                )
            }
        return SubscriptionView(
            team = WorkspaceRef(team.id!!, team.name),
            appliesTo = ownedTeams(team.ownerId).map { WorkspaceRef(it.id!!, it.name) },
            plan = PlanView.from(current?.second ?: freePlan()),
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

    /** 내가 결제한 구독 중 아직 확인하지 않은 만료 (최근 30일). 가장 최근 것부터 */
    fun expiryNotices(userId: Long, now: LocalDateTime = LocalDateTime.now()): List<ExpiryNoticeView> =
        subscriptionRepository.findAllByUserIdAndStatusIn(userId, listOf(SubscriptionStatus.EXPIRED))
            .filter { it.expiryAcknowledgedAt == null && it.expiredAt?.isAfter(now.minusDays(NOTICE_DAYS)) == true }
            .sortedByDescending { it.expiredAt }
            .map {
                val team = it.teamId?.let(teamRepository::findTeamById)
                ExpiryNoticeView(
                    subscriptionId = it.id!!,
                    plan = PlanView.from(planById(it.planId)),
                    expiredAt = it.expiredAt,
                    teamId = team?.id,
                    teamName = team?.name,
                    currentPlan = PlanView.from(team?.id?.let { id -> planOfTeam(id, now) } ?: freePlan()),
                )
            }

    /** 회원가입 직후 FREE 구독 생성 (기간 없음, 어디에도 적용 안 됨 — 예전 구조와 맞추려고 남겨 둔다) */
    @Transactional
    fun startFreeSubscription(userId: Long): Subscription =
        subscriptionRepository.save(Subscription(userId = userId, planId = freePlan().id!!))

    /**
     * 결제가 성공한 뒤 소유자의 구독 기간을 늘린다 (teamId = 결제할 때 고른 팀). 같은 플랜 구독이 살아 있으면 그 뒤에 이어 붙이고, 없으면 오늘부터 새 구독.
     * 다른 플랜 구독은 건드리지 않는다 (높은 플랜이 끝나면 남은 낮은 플랜으로 돌아간다).
     */
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
        // 같은 플랜을 다시 결제했으면 그 플랜의 만료 안내는 필요 없다
        subscriptionRepository.findAllByUserIdAndStatusIn(payerId, listOf(SubscriptionStatus.EXPIRED))
            .filter { it.planId == plan.id && it.expiryAcknowledgedAt == null }
            .forEach { it.acknowledgeExpiry(now) }
        return subscriptionRepository.save(subscription)
    }

    /** 자동결제 켜기·끄기 (결제한 사람만). 끄면 기간이 끝날 때 결제하지 않고 만료된다. 켜려면 카드가 있어야 한다 */
    @Transactional
    fun setAutoRenew(userId: Long, subscriptionId: Long, autoRenew: Boolean): SubscriptionView {
        val subscription = subscriptionRepository.findSubscriptionById(subscriptionId)
            ?.takeIf { it.userId == userId && it.isLive && it.currentPeriodEnd != null && it.teamId != null }
            ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        if (autoRenew && billingKeyRepository.findByUserIdAndActiveTrue(userId) == null) {
            throw BusinessException(ErrorCode.BILLING_CARD_REQUIRED)
        }
        subscription.autoRenew = autoRenew
        return workspaceSubscription(subscription.teamId!!, userId)
    }

    /** "플랜이 만료됐어요" 창을 닫음 */
    @Transactional
    fun acknowledgeExpiry(userId: Long, subscriptionId: Long) {
        val subscription = subscriptionRepository.findSubscriptionById(subscriptionId)?.takeIf { it.userId == userId }
            ?: throw BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND)
        subscription.acknowledgeExpiry()
    }

    /**
     * 팀을 지울 때 (team.delete() 전에). 플랜은 소유자의 것이라 다른 팀이 남아 있으면
     * 이 팀으로 결제한 구독과 한도 초과 설정을 그 팀으로 옮긴다. 마지막 팀이면 구독을 끝낸다 (자동결제 멈춤).
     */
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

    /** 결제할 수 있는 사람인지: 워크스페이스 소유자 */
    fun requireOwnedTeam(teamId: Long, userId: Long): Team {
        val team = teamRepository.findTeamById(teamId)?.takeIf { it.status == TeamStatus.ACTIVE } ?: throw BusinessException(ErrorCode.TEAM_NOT_FOUND)
        if (team.ownerId != userId) throw BusinessException(ErrorCode.NOT_TEAM_OWNER)
        return team
    }

    fun planById(id: Long): Plan = planRepository.findPlanById(id) ?: freePlan()

    /** 팀(= 그 소유자)에 지금 적용 중인 (구독, 플랜). 유료 구독이 없으면 null */
    fun inUseSubscription(teamId: Long, now: LocalDateTime = LocalDateTime.now()): Pair<Subscription, Plan>? =
        teamRepository.findTeamById(teamId)?.let { inUse(liveOfOwner(it.ownerId), now) }

    /** 워크스페이스 멤버만 (요금제·초과 인원 화면) */
    fun requireMemberTeamOf(teamId: Long, userId: Long): Team = requireMemberTeam(teamId, userId)

    fun cardView(key: BillingKey) = CardView(key.cardCompany, key.cardNumberMasked, key.createdDt)

    // ---------------------------------------------------------------

    private fun liveOf(teamId: Long) = subscriptionRepository.findAllByTeamIdAndStatusIn(teamId, LIVE)

    /** 소유자가 결제한 구독 중 살아 있는 것 (소유자의 어느 팀으로 결제했든) */
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

    /** 지금 적용 중인 (구독, 플랜). 쓸 수 있는 구독 중 가장 높은 플랜 */
    private fun inUse(live: List<Subscription>, now: LocalDateTime): Pair<Subscription, Plan>? =
        live.filter { it.isUsable(now) && it.currentPeriodEnd != null }
            .mapNotNull { s -> planRepository.findPlanById(s.planId)?.let { s to it } }
            .maxWithOrNull(compareBy<Pair<Subscription, Plan>> { it.second.sortOrder }.thenBy { it.first.currentPeriodEnd })

    private fun freePlan(): Plan =
        planRepository.findByCode(Plan.FREE_CODE)
            ?: throw IllegalStateException("plans 테이블에 FREE 플랜이 없습니다. db/seed.sql 을 실행하세요.")

    companion object {
        /** 이 기간 안에 끝난 구독만 만료 창으로 알린다 */
        const val NOTICE_DAYS = 30L
        private val LIVE = listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE)
    }
}
