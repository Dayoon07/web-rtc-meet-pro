package com.webrtcmeetpro.application.team

import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.domain.team.Team
import com.webrtcmeetpro.domain.team.TeamMember
import com.webrtcmeetpro.domain.team.TeamMemberRepository
import com.webrtcmeetpro.domain.team.TeamMemberStatus
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamStatus
import org.springframework.stereotype.Component

/** 채팅·회의 컨텍스트가 "이 사람이 이 팀 팀원인가" 를 확인할 때 쓰는 공용 진입점 */
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

    fun activeMemberIds(teamId: Long): Set<Long> =
        memberRepository.findAllByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE).map { it.userId }.toSet()
}
