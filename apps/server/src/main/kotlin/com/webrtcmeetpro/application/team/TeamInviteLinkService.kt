package com.webrtcmeetpro.application.team

import com.webrtcmeetpro.domain.team.dto.res.InviteLinkJoinedResDto
import com.webrtcmeetpro.domain.team.dto.res.InviteLinkPreviewResDto
import com.webrtcmeetpro.domain.team.dto.res.InviteLinkResDto
import com.webrtcmeetpro.application.notification.NotificationService
import com.webrtcmeetpro.application.plan.MemberOverageService
import com.webrtcmeetpro.application.plan.PlanService
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.team.Team
import com.webrtcmeetpro.domain.team.TeamInviteLink
import com.webrtcmeetpro.domain.team.TeamInviteLinkRepository
import com.webrtcmeetpro.domain.team.TeamMember
import com.webrtcmeetpro.domain.team.TeamMemberRepository
import com.webrtcmeetpro.domain.team.TeamMemberStatus
import com.webrtcmeetpro.domain.team.TeamPermission
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamRole
import com.webrtcmeetpro.domain.team.TeamStatus
import com.webrtcmeetpro.domain.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

/**
 * 팀 초대 링크: 이메일 초대 없이 링크만 나눠 주면 받은 사람이 팀에 들어온다.
 * - 팀 소유자·관리자(INVITE_MEMBER)가 만들고 지운다. 기간 제한은 없다.
 * - 비밀번호를 걸면 들어올 때 비밀번호를 물어본다 (BCrypt 로 저장).
 * - 들어올 때 멤버 한도는 이메일 초대 수락과 같다 (소유자의 모든 팀 합계, 이미 다른 팀에 있는 사람은 자리를 더 차지하지 않음).
 */
@Service
@Transactional(readOnly = true)
class TeamInviteLinkService(
    private val links: TeamInviteLinkRepository,
    private val teams: TeamRepository,
    private val members: TeamMemberRepository,
    private val users: UserRepository,
    private val access: TeamAccess,
    private val planService: PlanService,
    private val overage: MemberOverageService,
    private val notifications: NotificationService,
    private val passwordEncoder: PasswordEncoder,
) {
    private val random = SecureRandom()

    fun list(teamId: Long, userId: Long): List<InviteLinkResDto> {
        access.requirePermission(teamId, userId, TeamPermission.INVITE_MEMBER)
        val list = links.findAllByTeamIdAndDeletedFalseOrderByIdDesc(teamId)
        val names = users.findAllByIdIn(list.map { it.createdBy }.distinct()).associate { it.id!! to it.nickname }
        return list.map { view(it, names[it.createdBy]) }
    }

    /** 링크 만들기. password 가 비어 있으면 링크만으로 들어올 수 있다 */
    @Transactional
    fun create(teamId: Long, userId: Long, password: String?): InviteLinkResDto {
        access.requirePermission(teamId, userId, TeamPermission.INVITE_MEMBER)
        access.requireTeam(teamId)
        val pw = password?.takeIf { it.isNotBlank() }
        if (pw != null && pw.length !in PASSWORD_LENGTH) throw BusinessException(ErrorCode.INVALID_INVITE_LINK_PASSWORD, mapOf("min" to PASSWORD_LENGTH.first, "max" to PASSWORD_LENGTH.last))
        val link = links.save(TeamInviteLink(teamId, newToken(), pw?.let(passwordEncoder::encode), userId))
        return view(link, users.findUserById(userId)?.nickname)
    }

    /** 링크 지우기: 그 링크로는 더 못 들어온다 (이미 들어온 사람은 그대로) */
    @Transactional
    fun delete(teamId: Long, linkId: Long, userId: Long) {
        access.requirePermission(teamId, userId, TeamPermission.INVITE_MEMBER)
        val link = links.findInviteLinkById(linkId)?.takeIf { it.teamId == teamId && !it.deleted } ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)
        link.delete()
    }

    /** 링크를 연 사람에게 보여 줄 팀 소개 (로그인한 사람이면 누구나) */
    fun preview(token: String, userId: Long): InviteLinkPreviewResDto {
        val (link, team) = requireLink(token)
        return InviteLinkPreviewResDto(
            teamId = team.id!!,
            teamName = team.name,
            teamDescription = team.description,
            memberCount = members.countByTeamIdAndStatus(team.id!!, TeamMemberStatus.ACTIVE).toInt(),
            hasPassword = link.hasPassword(),
            alreadyMember = access.isMember(team.id!!, userId),
        )
    }

    /** 수락: 비밀번호를 걸었으면 맞아야 하고, 멤버 한도 안이면 바로 팀 멤버가 된다. 이미 멤버면 그대로 */
    @Transactional
    fun join(token: String, userId: Long, password: String?): InviteLinkJoinedResDto {
        val (link, team) = requireLink(token)
        val teamId = team.id!!
        if (access.isMember(teamId, userId)) return InviteLinkJoinedResDto(teamId, team.name)
        if (link.hasPassword()) {
            if (password.isNullOrEmpty()) throw BusinessException(ErrorCode.INVITE_LINK_PASSWORD_REQUIRED)
            if (!passwordEncoder.matches(password, link.passwordHash)) throw BusinessException(ErrorCode.INVITE_LINK_WRONG_PASSWORD)
        }
        // 멤버 한도: 소유자의 모든 팀 사람 수 (이미 다른 팀에 있는 사람은 자리를 더 차지하지 않는다)
        val ownerMembers = planService.ownerMemberIds(team.ownerId)
        if (userId !in ownerMembers) overage.requireSeat(team, planService.planOfTeam(teamId), ownerMembers.size.toLong())

        val existing = members.findByTeamIdAndUserId(teamId, userId)
        when {
            existing == null -> members.save(TeamMember(teamId, userId, TeamRole.MEMBER))
            existing.status != TeamMemberStatus.ACTIVE -> existing.rejoin()
        }
        link.joined()
        // 한도를 넘었으면 결제일을 잡고 소유자에게 경고
        overage.onMemberJoined(teamId)
        // 링크를 만든 사람에게 "○○님이 팀에 참여했어요"
        val me = users.findUserById(userId)
        notifications.notify(
            listOf(link.createdBy),
            NotificationType.TEAM_INVITATION_ACCEPTED,
            mapOf("teamName" to team.name, "memberName" to (me?.nickname ?: "")),
            "/app/teams/$teamId",
        )
        return InviteLinkJoinedResDto(teamId, team.name)
    }

    private fun requireLink(token: String): Pair<TeamInviteLink, Team> {
        val link = links.findByTokenAndDeletedFalse(token) ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)
        val team = teams.findTeamById(link.teamId)?.takeIf { it.status == TeamStatus.ACTIVE } ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)
        return link to team
    }

    private fun view(link: TeamInviteLink, createdByName: String?) =
        InviteLinkResDto(link.id!!, link.token, link.hasPassword(), link.joinCount, createdByName, link.createdDt)

    private fun newToken() = ByteArray(24).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    companion object {
        /** 링크 비밀번호 길이 */
        val PASSWORD_LENGTH = 4..64
    }
}
