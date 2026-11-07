package com.webrtcmeetpro.application.team

import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.application.notification.NotificationService
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.application.plan.PlanService
import com.webrtcmeetpro.domain.team.InvitationStatus
import com.webrtcmeetpro.domain.team.Team
import com.webrtcmeetpro.domain.team.TeamInvitation
import com.webrtcmeetpro.domain.team.TeamInvitationRepository
import com.webrtcmeetpro.domain.team.TeamMember
import com.webrtcmeetpro.domain.team.TeamMemberRepository
import com.webrtcmeetpro.domain.team.TeamMemberStatus
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamPermission
import com.webrtcmeetpro.domain.team.TeamRole
import com.webrtcmeetpro.domain.team.TeamStatus
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

data class TeamSummaryView(
    val id: Long,
    val name: String,
    val description: String?,
    val ownerId: Long,
    val myRole: TeamRole,
    /** 내 역할로 이 팀에서 할 수 있는 일. 웹·앱은 역할을 해석하지 않고 이 목록으로 버튼을 보여 준다 */
    val myPermissions: List<TeamPermission>,
    val memberCount: Int,
    val createdAt: LocalDateTime,
)

data class TeamMemberView(
    val userId: Long,
    val nickname: String,
    val email: String,
    val profileImageUrl: String?,
    val role: TeamRole,
    val joinedAt: LocalDateTime,
)

data class TeamLimitsView(val maxMembers: Int, val maxGroupRooms: Int, val pendingInvitations: Int)

data class TeamDetailView(val team: TeamSummaryView, val members: List<TeamMemberView>, val limits: TeamLimitsView)

data class InvitationView(
    val id: Long,
    val teamId: Long,
    val teamName: String,
    val inviterName: String,
    val inviteeEmail: String,
    val status: InvitationStatus,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
)

@Service
@Transactional(readOnly = true)
class TeamService(
    private val teamRepository: TeamRepository,
    private val memberRepository: TeamMemberRepository,
    private val invitationRepository: TeamInvitationRepository,
    private val userRepository: UserRepository,
    private val planService: PlanService,
    private val notifications: NotificationService,
    private val access: TeamAccess,
) {
    private val random = SecureRandom()

    @Transactional
    fun create(ownerId: Long, name: String, description: String?): TeamSummaryView {
        val plan = planService.currentPlanOf(ownerId)
        val owned = teamRepository.countByOwnerIdAndStatus(ownerId, TeamStatus.ACTIVE)
        if (!plan.canOwnMoreTeams(owned)) throw BusinessException(ErrorCode.PLAN_TEAM_LIMIT, mapOf("max" to plan.maxOwnedTeams))

        val team = teamRepository.save(Team(ownerId, name.trim(), description?.trim()?.ifBlank { null }))
        memberRepository.save(TeamMember(team.id!!, ownerId, TeamRole.OWNER))
        return summary(team, TeamRole.OWNER, 1)
    }

    fun myTeams(userId: Long): List<TeamSummaryView> {
        val memberships = memberRepository.findAllByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE).associateBy { it.teamId }
        return teamRepository.findAllByIdInAndStatus(memberships.keys, TeamStatus.ACTIVE)
            .sortedBy { it.createdDt }
            .map { summary(it, memberships.getValue(it.id!!).role, memberRepository.countByTeamIdAndStatus(it.id!!, TeamMemberStatus.ACTIVE).toInt()) }
    }

    fun detail(teamId: Long, userId: Long): TeamDetailView {
        val me = access.requireMember(teamId, userId)
        val team = access.requireTeam(teamId)
        val members = memberRepository.findAllByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE)
        val users = userRepository.findAllByIdIn(members.map { it.userId }).associateBy { it.id!! }
        val plan = planService.currentPlanOf(team.ownerId)
        val pending = invitationRepository.findAllByTeamIdAndStatus(teamId, InvitationStatus.PENDING).count { it.isPending() }
        return TeamDetailView(
            team = summary(team, me.role, members.size),
            members = members
                .mapNotNull { m -> users[m.userId]?.let { memberView(m, it) } }
                .sortedWith(compareBy({ it.role.ordinal }, { it.joinedAt })),
            limits = TeamLimitsView(plan.maxMembersPerTeam, plan.maxGroupRoomsPerTeam, pending),
        )
    }

    @Transactional
    fun invite(teamId: Long, inviterId: Long, rawEmail: String): InvitationView {
        access.requirePermission(teamId, inviterId, TeamPermission.INVITE_MEMBER)
        val team = access.requireTeam(teamId)
        val inviterUser = findUser(inviterId)
        val email = rawEmail.trim().lowercase()
        if (email == inviterUser.email) throw BusinessException(ErrorCode.CANNOT_INVITE_SELF)

        val invitee = userRepository.findByEmail(email)
        if (invitee != null && memberRepository.findByTeamIdAndUserId(teamId, invitee.id!!)?.status == TeamMemberStatus.ACTIVE) {
            throw BusinessException(ErrorCode.ALREADY_TEAM_MEMBER)
        }
        val pending = invitationRepository.findAllByTeamIdAndStatus(teamId, InvitationStatus.PENDING).filter { it.isPending() }
        if (pending.any { it.inviteeEmail == email }) throw BusinessException(ErrorCode.INVITATION_ALREADY_PENDING)

        // 팀 인원 한도는 팀 "소유자" 의 플랜 기준. 대기 중인 초대도 자리를 차지한다
        val plan = planService.currentPlanOf(team.ownerId)
        val active = memberRepository.countByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE)
        if (!plan.canAddMember(active + pending.size)) {
            throw BusinessException(ErrorCode.PLAN_MEMBER_LIMIT, mapOf("max" to plan.maxMembersPerTeam))
        }

        val invitation = invitationRepository.save(
            TeamInvitation(
                teamId = teamId,
                inviterId = inviterId,
                inviteeEmail = email,
                inviteeId = invitee?.id,
                token = newToken(),
                expiresAt = LocalDateTime.now().plusDays(INVITATION_DAYS),
            ),
        )
        invitee?.let {
            notifications.notify(
                listOf(it.id!!),
                NotificationType.TEAM_INVITATION,
                mapOf("teamName" to team.name, "inviterName" to inviterUser.nickname),
                "/app/invitations",
            )
        }
        return invitationView(invitation, team, inviterUser)
    }

    fun teamInvitations(teamId: Long, userId: Long): List<InvitationView> {
        access.requireMember(teamId, userId)
        val team = access.requireTeam(teamId)
        val invitations = invitationRepository.findAllByTeamIdAndStatus(teamId, InvitationStatus.PENDING).filter { it.isPending() }
        val inviters = userRepository.findAllByIdIn(invitations.map { it.inviterId }).associateBy { it.id!! }
        return invitations.map { invitationView(it, team, inviters[it.inviterId]) }
    }

    /** 내 이메일로 온 대기 중인 초대 (가입 전에 받은 초대도 포함) */
    fun myInvitations(userId: Long): List<InvitationView> {
        val user = findUser(userId)
        val invitations = invitationRepository.findAllByInviteeEmailAndStatus(user.email, InvitationStatus.PENDING).filter { it.isPending() }
        val teams = teamRepository.findAllByIdInAndStatus(invitations.map { it.teamId }, TeamStatus.ACTIVE).associateBy { it.id!! }
        val inviters = userRepository.findAllByIdIn(invitations.map { it.inviterId }).associateBy { it.id!! }
        return invitations.mapNotNull { inv -> teams[inv.teamId]?.let { invitationView(inv, it, inviters[inv.inviterId]) } }
    }

    @Transactional
    fun accept(invitationId: Long, userId: Long): TeamSummaryView {
        val user = findUser(userId)
        val invitation = findMyInvitation(invitationId, user)
        val team = access.requireTeam(invitation.teamId)

        val plan = planService.currentPlanOf(team.ownerId)
        val active = memberRepository.countByTeamIdAndStatus(team.id!!, TeamMemberStatus.ACTIVE)
        if (!plan.canAddMember(active)) throw BusinessException(ErrorCode.PLAN_MEMBER_LIMIT, mapOf("max" to plan.maxMembersPerTeam))

        val existing = memberRepository.findByTeamIdAndUserId(team.id!!, userId)
        when {
            existing == null -> memberRepository.save(TeamMember(team.id!!, userId, TeamRole.MEMBER))
            existing.status != TeamMemberStatus.ACTIVE -> existing.rejoin()
        }
        invitation.accept()
        notifications.notify(
            listOf(invitation.inviterId),
            NotificationType.TEAM_INVITATION_ACCEPTED,
            mapOf("teamName" to team.name, "memberName" to user.nickname),
            "/app/teams/${team.id}",
        )
        return summary(team, TeamRole.MEMBER, (active + 1).toInt())
    }

    @Transactional
    fun decline(invitationId: Long, userId: Long) {
        findMyInvitation(invitationId, findUser(userId)).decline()
    }

    @Transactional
    fun cancel(teamId: Long, invitationId: Long, userId: Long) {
        access.requirePermission(teamId, userId, TeamPermission.INVITE_MEMBER)
        val invitation = invitationRepository.findInvitationById(invitationId)?.takeIf { it.teamId == teamId && it.isPending() }
            ?: throw BusinessException(ErrorCode.INVITATION_NOT_FOUND)
        invitation.cancel()
    }

    private fun findMyInvitation(invitationId: Long, user: User): TeamInvitation =
        invitationRepository.findInvitationById(invitationId)?.takeIf { it.inviteeEmail == user.email && it.isPending() }
            ?: throw BusinessException(ErrorCode.INVITATION_NOT_FOUND)

    private fun findUser(userId: Long) = userRepository.findUserById(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

    private fun newToken() = ByteArray(24).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun summary(team: Team, role: TeamRole, memberCount: Int) =
        TeamSummaryView(team.id!!, team.name, team.description, team.ownerId, role, role.permissions.sorted(), memberCount, team.createdDt)

    private fun memberView(m: TeamMember, u: User) =
        TeamMemberView(u.id!!, u.nickname, u.email, u.profileImageUrl, m.role, m.joinedAt)

    private fun invitationView(inv: TeamInvitation, team: Team, inviter: User?) = InvitationView(
        id = inv.id!!,
        teamId = team.id!!,
        teamName = team.name,
        inviterName = inviter?.nickname ?: "",
        inviteeEmail = inv.inviteeEmail,
        status = inv.status,
        expiresAt = inv.expiresAt,
        createdAt = inv.createdDt,
    )

    companion object {
        const val INVITATION_DAYS = 7L
    }
}
