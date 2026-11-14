package com.webrtcmeetpro.application.team

import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.domain.team.Team
import com.webrtcmeetpro.domain.team.TeamMember
import com.webrtcmeetpro.domain.team.TeamMemberRepository
import com.webrtcmeetpro.domain.team.TeamMemberStatus
import com.webrtcmeetpro.domain.team.TeamPermission
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamStatus
import org.springframework.stereotype.Component

/** 역할별 권한은 TeamRole 한 곳에서 정하고, 팀·채팅·회의 서비스는 여기서만 확인한다 */
@Component
class TeamAccess(
    private val teamRepository: TeamRepository,
    private val memberRepository: TeamMemberRepository,
) {
    fun requireTeam(teamId: Long): Team =
        teamRepository.findTeamById(teamId)?.takeIf { it.status == TeamStatus.ACTIVE }
            ?: throw BusinessException(ErrorCode.TEAM_NOT_FOUND)

    fun requireMember(teamId: Long, userId: Long): TeamMember {
        requireTeam(teamId)
        return memberRepository.findByTeamIdAndUserId(teamId, userId)?.takeIf { it.status == TeamMemberStatus.ACTIVE }
            ?: throw BusinessException(ErrorCode.NOT_TEAM_MEMBER)
    }

    fun requirePermission(teamId: Long, userId: Long, permission: TeamPermission): TeamMember {
        val member = requireMember(teamId, userId)
        if (!member.can(permission)) throw BusinessException(permission.deniedError)
        return member
    }

    fun isMember(teamId: Long, userId: Long): Boolean =
        memberRepository.findByTeamIdAndUserId(teamId, userId)?.status == TeamMemberStatus.ACTIVE

    /** 화면에 버튼을 보여 줄지 정할 때처럼 예외 없이 확인 */
    fun can(teamId: Long, userId: Long, permission: TeamPermission): Boolean =
        memberRepository.findByTeamIdAndUserId(teamId, userId)?.can(permission) == true

    fun activeMemberIds(teamId: Long): Set<Long> =
        memberRepository.findAllByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE).map { it.userId }.toSet()
}
