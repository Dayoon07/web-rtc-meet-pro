package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.domain.plan.dto.res.OverageResDto
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

data class OverageBilling(val withRenewal: Boolean, val chargeAt: LocalDateTime?)

/**
 * 플랜 멤버 한도를 넘는 인원 요금. 소유자가 동의하고 카드를 등록해야 초과 초대가 가능하다.
 * 인원은 소유자의 모든 팀을 합쳐 한 사람은 한 번만 센다.
 * 월 자동결제 플랜이면 갱신 결제에 합치고, 아니면 한 달마다 따로 청구한다.
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
    fun memberCount(team: Team): Long = planService.ownerMemberCount(team.ownerId)

    /** 설정을 팀 하나에만 두어야 소유자당 한 번만 청구된다. 허용을 켠 팀, 없으면 처음 만든 팀 */
    fun settingsTeam(team: Team): Team {
        val owned = planService.ownedTeams(team.ownerId)
        return owned.firstOrNull { it.overageAllowed } ?: owned.firstOrNull() ?: team
    }

    /** 허용하지 않았으면 청구할 수 없으므로 0 */
    fun billableExcess(team: Team, plan: Plan = planService.planOfTeam(team.id!!)): Long =
        if (!settingsTeam(team).overageAllowed) 0 else (memberCount(team) - plan.maxMembersPerTeam).coerceAtLeast(0)

    fun billing(team: Team, now: LocalDateTime = LocalDateTime.now()): OverageBilling {
        val inUse = planService.inUseSubscription(team.id!!, now)
        val withRenewal = inUse != null &&
            inUse.first.billingCycle == BillingCycle.MONTHLY &&
            inUse.first.autoRenew &&
            billingKeys.findByUserIdAndActiveTrue(inUse.first.userId) != null
        return OverageBilling(withRenewal, if (withRenewal) inUse!!.first.currentPeriodEnd else settingsTeam(team).overageNextChargeAt)
    }

    fun view(teamId: Long, viewerId: Long, now: LocalDateTime = LocalDateTime.now()): OverageResDto {
        val team = planService.requireMemberTeamOf(teamId, viewerId)
        val settings = settingsTeam(team)
        val plan = planService.planOfTeam(teamId, now)
        val count = memberCount(team)
        val excess = (count - plan.maxMembersPerTeam).coerceAtLeast(0)
        val billing = billing(team, now)
        return OverageResDto(
            teamId = teamId,
            allowed = settings.overageAllowed,
            agreedAt = settings.overageAgreedAt,
            members = count,
            limit = plan.maxMembersPerTeam,
            excess = excess,
            pricePerMember = plan.overagePricePerMember,
            nextAmount = if (settings.overageAllowed) excess * plan.overagePricePerMember else 0,
            nextChargeAt = billing.chargeAt.takeIf { settings.overageAllowed && excess > 0 },
            withRenewal = billing.withRenewal,
            canManage = team.ownerId == viewerId,
            hasCard = billingKeys.findByUserIdAndActiveTrue(team.ownerId) != null,
            hardCap = HARD_CAP,
        )
    }

    /** 한도를 넘은 채로 끄면 결제를 피할 수 있으므로, 넘은 멤버가 있으면 끌 수 없다 */
    @Transactional
    fun setAllowed(teamId: Long, userId: Long, allowed: Boolean, agree: Boolean, now: LocalDateTime = LocalDateTime.now()): OverageResDto {
        val team = planService.requireOwnedTeam(teamId, userId)
        val settings = settingsTeam(team)
        if (allowed) {
            if (!agree) throw BusinessException(ErrorCode.OVERAGE_CONSENT_REQUIRED)
            billingKeys.findByUserIdAndActiveTrue(userId) ?: throw BusinessException(ErrorCode.BILLING_CARD_REQUIRED)
            if (!settings.overageAllowed) settings.allowOverage(userId, now)
        } else {
            val excess = (memberCount(team) - planService.planOfTeam(teamId, now).maxMembersPerTeam).coerceAtLeast(0)
            if (settings.overageAllowed && excess > 0) throw BusinessException(ErrorCode.OVERAGE_IN_USE, mapOf("count" to excess))
            planService.ownedTeams(team.ownerId).forEach { it.disallowOverage() }
        }
        return view(teamId, userId, now)
    }

    /** @param seats 소유자의 모든 팀 사람 + 대기 중인 초대 */
    fun requireSeat(team: Team, plan: Plan, seats: Long) {
        if (plan.canAddMember(seats)) return
        if (!settingsTeam(team).overageAllowed) {
            throw BusinessException(ErrorCode.PLAN_MEMBER_LIMIT, mapOf("max" to plan.maxMembersPerTeam, "overage" to true))
        }
        if (seats >= HARD_CAP) throw BusinessException(ErrorCode.OVERAGE_MAX_MEMBERS, mapOf("max" to HARD_CAP))
    }

    /** 경고 메일은 하루 한 번까지 */
    @Transactional
    fun onMemberJoined(teamId: Long, now: LocalDateTime = LocalDateTime.now()) {
        val team = teams.findTeamById(teamId) ?: return
        val settings = settingsTeam(team)
        val plan = planService.planOfTeam(teamId, now)
        val excess = billableExcess(team, plan)
        if (excess <= 0) return
        val billing = billing(team, now)
        if (!billing.withRenewal) settings.scheduleOverage(now.plusMonths(1))
        if (!settings.shouldWarnOverage(now)) return
        settings.overageWarned(now)
        val chargeAt = if (billing.withRenewal) billing.chargeAt else settings.overageNextChargeAt
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
                    owner.email, MailLanguage.KO, team.name, memberCount(team), plan.maxMembersPerTeam, excess,
                    plan.overagePricePerMember, chargeAt, web.link(path),
                ),
            )
        }
    }

    fun noticeCharge(team: Team, plan: Plan, planLabel: String?, planAmount: Long, excess: Long, chargeAt: LocalDateTime) {
        val settings = settingsTeam(team)
        if (excess <= 0 || settings.overageNoticeFor == chargeAt) return
        settings.overageNoticeSent(chargeAt)
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
        /** 실수로 큰 금액이 청구되지 않게 막는 상한 */
        const val HARD_CAP = 1000
    }
}
