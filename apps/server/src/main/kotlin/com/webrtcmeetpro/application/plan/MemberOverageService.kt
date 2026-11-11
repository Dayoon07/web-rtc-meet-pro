package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.application.mail.MailLanguage
import com.webrtcmeetpro.application.mail.MailService
import com.webrtcmeetpro.application.mail.MailTemplates
import com.webrtcmeetpro.application.notification.NotificationService
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.JacksonConfig
import com.webrtcmeetpro.config.properties.WebProperties
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.BillingKeyRepository
import com.webrtcmeetpro.domain.plan.Plan
import com.webrtcmeetpro.domain.team.Team
import com.webrtcmeetpro.domain.team.TeamMemberRepository
import com.webrtcmeetpro.domain.team.TeamMemberStatus
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 워크스페이스 요금제 화면의 "한도 초과" 칸 */
data class OverageView(
    val teamId: Long,
    /** 소유자가 한도 초과를 허용했는지 (= 초과 요금 동의) */
    val allowed: Boolean,
    val agreedAt: LocalDateTime?,
    val members: Long,
    val limit: Int,
    /** 지금 한도를 넘은 인원 */
    val excess: Long,
    val pricePerMember: Long,
    /** 지금 인원이면 다음 결제에 더해질 금액 */
    val nextAmount: Long,
    /** 초과 요금이 들어갈 다음 결제일 (월 자동결제면 플랜 갱신일) */
    val nextChargeAt: LocalDateTime?,
    /** 월 자동결제 플랜 갱신 때 같이 내는지 (아니면 한 달마다 따로) */
    val withRenewal: Boolean,
    /** 소유자만 바꿀 수 있다 */
    val canManage: Boolean,
    /** 소유자 카드가 등록돼 있는지 (켜려면 필요) */
    val hasCard: Boolean,
    /** 한 워크스페이스 최대 인원 */
    val hardCap: Int,
)

/** 한도 초과 요금이 들어갈 결제 방식 */
data class OverageBilling(val withRenewal: Boolean, val chargeAt: LocalDateTime?)

/**
 * 멤버 초과 요금. 어느 플랜이든 소유자가 동의하고 켜면(카드 필요) 플랜 멤버 한도를 넘게 초대할 수 있고,
 * 넘은 인원 × plans.overage_price_per_member 원을 한 달마다 낸다 (결제일의 멤버 수 기준).
 * - 월 자동결제 플랜: 갱신 결제 때 (플랜 요금 + 초과 요금) 한 번에 (BillingService.renew)
 * - 그 밖 (무료·연 결제·자동결제 꺼짐): 처음 넘은 날부터 한 달마다 초과 요금만 따로 (BillingService.processOverage)
 * 한도를 넘으면 소유자에게 경고 메일(하루 한 번까지), 결제 3일 전에 금액 안내 메일.
 */
@Service
@Transactional(readOnly = true)
class MemberOverageService(
    private val planService: PlanService,
    private val teams: TeamRepository,
    private val members: TeamMemberRepository,
    private val billingKeys: BillingKeyRepository,
    private val users: UserRepository,
    private val notifications: NotificationService,
    private val mail: MailService,
    private val web: WebProperties,
) {
    fun memberCount(teamId: Long): Long = members.countByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE)

    /** 지금 한도를 넘은 인원 (허용하지 않은 워크스페이스는 청구하지 않으므로 0) */
    fun billableExcess(team: Team, plan: Plan = planService.planOfTeam(team.id!!)): Long =
        if (!team.overageAllowed) 0 else (memberCount(team.id!!) - plan.maxMembersPerTeam).coerceAtLeast(0)

    /** 월 자동결제 중인 플랜이면 그 갱신일에 같이, 아니면 따로 */
    fun billing(team: Team, now: LocalDateTime = LocalDateTime.now()): OverageBilling {
        val inUse = planService.inUseSubscription(team.id!!, now)
        val withRenewal = inUse != null &&
            inUse.first.billingCycle == BillingCycle.MONTHLY &&
            inUse.first.autoRenew &&
            billingKeys.findByUserIdAndActiveTrue(inUse.first.userId) != null
        return OverageBilling(withRenewal, if (withRenewal) inUse!!.first.currentPeriodEnd else team.overageNextChargeAt)
    }

    fun view(teamId: Long, viewerId: Long, now: LocalDateTime = LocalDateTime.now()): OverageView {
        val team = planService.requireMemberTeamOf(teamId, viewerId)
        val plan = planService.planOfTeam(teamId, now)
        val count = memberCount(teamId)
        val excess = (count - plan.maxMembersPerTeam).coerceAtLeast(0)
        val billing = billing(team, now)
        return OverageView(
            teamId = teamId,
            allowed = team.overageAllowed,
            agreedAt = team.overageAgreedAt,
            members = count,
            limit = plan.maxMembersPerTeam,
            excess = excess,
            pricePerMember = plan.overagePricePerMember,
            nextAmount = if (team.overageAllowed) excess * plan.overagePricePerMember else 0,
            nextChargeAt = billing.chargeAt.takeIf { team.overageAllowed && excess > 0 },
            withRenewal = billing.withRenewal,
            canManage = team.ownerId == viewerId,
            hasCard = billingKeys.findByUserIdAndActiveTrue(team.ownerId) != null,
            hardCap = HARD_CAP,
        )
    }

    /**
     * 켜기·끄기 (소유자만). 켜려면 카드가 있어야 하고 안내 문구에 동의(agree)해야 한다.
     * 끄는 건 지금 한도를 넘은 멤버가 없을 때만 (넘은 채로 끄고 결제를 피하지 못하게).
     */
    @Transactional
    fun setAllowed(teamId: Long, userId: Long, allowed: Boolean, agree: Boolean, now: LocalDateTime = LocalDateTime.now()): OverageView {
        val team = planService.requireOwnedTeam(teamId, userId)
        if (allowed) {
            if (!agree) throw BusinessException(ErrorCode.OVERAGE_CONSENT_REQUIRED)
            billingKeys.findByUserIdAndActiveTrue(userId) ?: throw BusinessException(ErrorCode.BILLING_CARD_REQUIRED)
            team.allowOverage(userId, now)
        } else {
            val excess = (memberCount(teamId) - planService.planOfTeam(teamId, now).maxMembersPerTeam).coerceAtLeast(0)
            if (team.overageAllowed && excess > 0) throw BusinessException(ErrorCode.OVERAGE_IN_USE, mapOf("count" to excess))
            team.disallowOverage()
        }
        return view(teamId, userId, now)
    }

    /**
     * 초대·수락 전에: 한도 안이면 통과, 넘으면 소유자가 허용했을 때만 (최대 HARD_CAP 명).
     * @param seats 지금 차지한 자리 (멤버 + 대기 중인 초대)
     */
    fun requireSeat(team: Team, plan: Plan, seats: Long) {
        if (plan.canAddMember(seats)) return
        if (!team.overageAllowed) {
            throw BusinessException(ErrorCode.PLAN_MEMBER_LIMIT, mapOf("max" to plan.maxMembersPerTeam, "overage" to true))
        }
        if (seats >= HARD_CAP) throw BusinessException(ErrorCode.OVERAGE_MAX_MEMBERS, mapOf("max" to HARD_CAP))
    }

    /** 멤버가 들어온 뒤: 한도를 넘었으면 결제일을 잡고 소유자에게 경고 (하루 한 번까지) */
    @Transactional
    fun onMemberJoined(teamId: Long, now: LocalDateTime = LocalDateTime.now()) {
        val team = teams.findTeamById(teamId) ?: return
        val plan = planService.planOfTeam(teamId, now)
        val excess = billableExcess(team, plan)
        if (excess <= 0) return
        val billing = billing(team, now)
        if (!billing.withRenewal) team.scheduleOverage(now.plusMonths(1))
        if (!team.shouldWarnOverage(now)) return
        team.overageWarned(now)
        val chargeAt = if (billing.withRenewal) billing.chargeAt else team.overageNextChargeAt
        val path = "/app/billing?team=$teamId"
        notifications.notify(
            listOf(team.ownerId),
            NotificationType.MEMBER_OVERAGE,
            mapOf(
                "teamName" to team.name, "excess" to excess, "limit" to plan.maxMembersPerTeam,
                "amount" to excess * plan.overagePricePerMember, "date" to chargeAt?.let(JacksonConfig::toOffsetString),
            ),
            path,
        )
        val owner = users.findUserById(team.ownerId) ?: return
        if (owner.email.isNotBlank()) {
            mail.sendAfterCommit(
                MailTemplates.memberOverage(
                    owner.email, MailLanguage.KO, team.name, memberCount(teamId), plan.maxMembersPerTeam, excess,
                    plan.overagePricePerMember, chargeAt, web.link(path),
                ),
            )
        }
    }

    /** 결제 3일 전 안내 (같은 결제일에는 한 번). BillingService 가 부른다 */
    fun noticeCharge(team: Team, plan: Plan, planLabel: String?, planAmount: Long, excess: Long, chargeAt: LocalDateTime) {
        if (excess <= 0 || team.overageNoticeFor == chargeAt) return
        team.overageNoticeSent(chargeAt)
        val path = "/app/billing?team=${team.id}"
        val total = planAmount + excess * plan.overagePricePerMember
        notifications.notify(
            listOf(team.ownerId),
            NotificationType.OVERAGE_CHARGE_SOON,
            mapOf("teamName" to team.name, "excess" to excess, "amount" to total, "date" to JacksonConfig.toOffsetString(chargeAt)),
            path,
        )
        val owner = users.findUserById(team.ownerId) ?: return
        if (owner.email.isNotBlank()) {
            mail.sendAfterCommit(
                MailTemplates.overageChargeSoon(
                    owner.email, MailLanguage.KO, team.name, planLabel, planAmount, excess, plan.overagePricePerMember, chargeAt, web.link(path),
                ),
            )
        }
    }

    companion object {
        /** 한 워크스페이스 최대 인원 (실수로 큰 금액이 청구되지 않게) */
        const val HARD_CAP = 1000
    }
}
