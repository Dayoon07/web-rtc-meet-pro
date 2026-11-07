package com.webrtcmeetpro.application.team

import com.webrtcmeetpro.application.mail.MailLanguage
import com.webrtcmeetpro.application.mail.MailService
import com.webrtcmeetpro.application.mail.MailTemplates
import com.webrtcmeetpro.config.properties.WebProperties
import com.webrtcmeetpro.domain.meeting.MeetingStatus
import com.webrtcmeetpro.domain.meeting.MeetingRepository
import com.webrtcmeetpro.domain.chat.ChatRoomRepository
import com.webrtcmeetpro.domain.chat.ChatRoomMemberRepository
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
    /** 이 화면을 보는 사람이 이 팀원을 내보낼 수 있는지 (OWNER: 누구나, ADMIN: 일반 팀원만) */
    val removable: Boolean = false,
)

/** maxGroupRooms: null = 제한 없음. 한도는 팀 소유자의 지금 플랜 기준 */
data class TeamLimitsView(
    val maxMembers: Int,
    val maxGroupRooms: Int?,
    val pendingInvitations: Int,
    val maxMeetingParticipants: Int = 0,
    val recordingEnabled: Boolean = false,
    val planCode: String = "",
)

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
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMemberRepository: ChatRoomMemberRepository,
    private val meetingRepository: MeetingRepository,
    private val mail: MailService,
    private val web: WebProperties,
) {
    private val random = SecureRandom()

    /** 워크스페이스 만들기. 개수 제한 없음 (요금제는 워크스페이스마다 따로, 처음엔 FREE) */
    @Transactional
    fun create(ownerId: Long, name: String, description: String?): TeamSummaryView {
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
        val plan = planService.planOfTeam(teamId)
        val pending = invitationRepository.findAllByTeamIdAndStatus(teamId, InvitationStatus.PENDING).count { it.isPending() }
        return TeamDetailView(
            team = summary(team, me.role, members.size),
            members = members
                .mapNotNull { m -> users[m.userId]?.let { memberView(m, it, removable = me.canRemove(m)) } }
                .sortedWith(compareBy({ it.role.ordinal }, { it.joinedAt })),
            limits = TeamLimitsView(plan.maxMembersPerTeam, plan.maxGroupRoomsPerTeam, pending, plan.maxMeetingParticipants, plan.recordingEnabled, plan.code),
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

        // 멤버 수 한도는 워크스페이스 플랜 기준. 대기 중인 초대도 자리를 차지한다
        val plan = planService.planOfTeam(teamId)
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
        // 이메일 인증 전인 계정에는 알리지 않는다 (남의 이메일로 가입한 사람일 수 있다)
        invitee?.takeIf { it.isEmailVerified }?.let {
            notifications.notify(
                listOf(it.id!!),
                NotificationType.TEAM_INVITATION,
                mapOf("teamName" to team.name, "inviterName" to inviterUser.nickname),
                "/app/invitations",
            )
        }
        // 초대 메일 (커밋된 뒤에 보낸다). 아직 가입 안 한 사람은 가입 화면으로 (같은 이메일로 가입하면 초대가 보인다)
        val link = web.link(if (invitee != null) "/app/invitations" else "/signup")
        mail.sendAfterCommit(
            MailTemplates.teamInvitation(email, MailLanguage.current(), team.name, inviterUser.nickname, link, registered = invitee != null),
        )
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
        val user = requireVerified(findUser(userId))
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

        val plan = planService.planOfTeam(team.id!!)
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

    /** 팀원 역할 바꾸기 (MANAGE_ROLES 권한 — 지금은 OWNER). OWNER 와 자기 자신은 바꿀 수 없다 */
    @Transactional
    fun changeRole(teamId: Long, actorId: Long, targetUserId: Long, role: TeamRole): TeamMemberView {
        access.requirePermission(teamId, actorId, TeamPermission.MANAGE_ROLES)
        if (targetUserId == actorId) throw BusinessException(ErrorCode.CANNOT_CHANGE_OWN_ROLE)
        val target = memberRepository.findByTeamIdAndUserId(teamId, targetUserId)?.takeIf { it.status == TeamMemberStatus.ACTIVE }
            ?: throw BusinessException(ErrorCode.NOT_TEAM_MEMBER)
        target.changeRole(role)
        return memberView(target, findUser(targetUserId))
    }

    /** 팀 나가기. 팀을 만든 사람(OWNER)은 나갈 수 없다 — 팀을 삭제한다 */
    @Transactional
    fun leave(teamId: Long, userId: Long) {
        val me = access.requireMember(teamId, userId)
        if (me.role == TeamRole.OWNER) throw BusinessException(ErrorCode.OWNER_CANNOT_LEAVE)
        me.leave()
        leaveTeamChatRooms(teamId, userId)
    }

    /** 팀원 내보내기 (REMOVE_MEMBER): OWNER 는 누구나, ADMIN 은 일반 팀원만. 그 팀의 채팅방에서도 나가게 한다 */
    @Transactional
    fun removeMember(teamId: Long, actorId: Long, targetUserId: Long) {
        val actor = access.requirePermission(teamId, actorId, TeamPermission.REMOVE_MEMBER)
        val target = memberRepository.findByTeamIdAndUserId(teamId, targetUserId)?.takeIf { it.status == TeamMemberStatus.ACTIVE }
            ?: throw BusinessException(ErrorCode.NOT_TEAM_MEMBER)
        if (!actor.canRemove(target)) throw BusinessException(ErrorCode.CANNOT_REMOVE_MEMBER)
        target.kick()
        leaveTeamChatRooms(teamId, targetUserId)
    }

    /**
     * 팀 삭제 (DELETE_TEAM — OWNER). 행은 지우지 않고 사용 안 함으로:
     * 예약 회의 취소 · 대기 중인 초대 취소 · 채팅방 사용 안 함 · 팀원 모두 나감 · 팀 DELETED
     */
    @Transactional
    fun delete(teamId: Long, userId: Long) {
        access.requirePermission(teamId, userId, TeamPermission.DELETE_TEAM)
        val team = access.requireTeam(teamId)
        meetingRepository.findAllByTeamIdAndStatus(teamId, MeetingStatus.SCHEDULED).forEach { it.cancel() }
        invitationRepository.findAllByTeamIdAndStatus(teamId, InvitationStatus.PENDING).filter { it.isPending() }.forEach { it.cancel() }
        chatRoomRepository.findAllByTeamIdAndDeletedFalse(teamId).forEach { it.delete() }
        memberRepository.findAllByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE).forEach { it.leave() }
        // 남은 요금제 구독도 끝낸다 (자동결제가 더 나가지 않게)
        planService.cancelTeamSubscriptions(teamId)
        team.delete()
    }

    /** 이 팀의 채팅방에서 나가게 한다 (안 읽은 개수·방 목록에서 빠지도록) */
    private fun leaveTeamChatRooms(teamId: Long, userId: Long) {
        val mine = chatMemberRepository.findAllByUserIdAndLeftAtIsNull(userId)
        if (mine.isEmpty()) return
        val teamRooms = chatRoomRepository.findAllByIdInAndDeletedFalse(mine.map { it.roomId }).filter { it.teamId == teamId }.map { it.id }.toSet()
        mine.filter { it.roomId in teamRooms }.forEach { it.leave() }
    }

    private fun findMyInvitation(invitationId: Long, user: User): TeamInvitation =
        invitationRepository.findInvitationById(invitationId)?.takeIf { it.inviteeEmail == requireVerified(user).email && it.isPending() }
            ?: throw BusinessException(ErrorCode.INVITATION_NOT_FOUND)

    /** 이메일로 온 초대는 그 이메일의 주인임을 확인한 사람만 보고 수락할 수 있다 */
    private fun requireVerified(user: User): User =
        user.takeIf { it.isEmailVerified } ?: throw BusinessException(ErrorCode.EMAIL_NOT_VERIFIED)

    private fun findUser(userId: Long) = userRepository.findUserById(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)

    private fun newToken() = ByteArray(24).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun summary(team: Team, role: TeamRole, memberCount: Int) =
        TeamSummaryView(team.id!!, team.name, team.description, team.ownerId, role, role.permissions.sorted(), memberCount, team.createdDt)

    private fun memberView(m: TeamMember, u: User, removable: Boolean = false) =
        TeamMemberView(u.id!!, u.nickname, u.email, u.profileImageUrl, m.role, m.joinedAt, removable)

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
