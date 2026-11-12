package com.webrtcmeetpro.infrastructure.team

import com.webrtcmeetpro.domain.team.Team
import com.webrtcmeetpro.domain.team.TeamInvitation
import com.webrtcmeetpro.domain.team.TeamInvitationRepository
import com.webrtcmeetpro.domain.team.TeamInviteLink
import com.webrtcmeetpro.domain.team.TeamInviteLinkRepository
import com.webrtcmeetpro.domain.team.TeamMember
import com.webrtcmeetpro.domain.team.TeamMemberRepository
import com.webrtcmeetpro.domain.team.TeamRepository
import org.springframework.data.jpa.repository.JpaRepository

interface TeamJpaRepository : JpaRepository<Team, Long>, TeamRepository

interface TeamMemberJpaRepository : JpaRepository<TeamMember, Long>, TeamMemberRepository

interface TeamInvitationJpaRepository : JpaRepository<TeamInvitation, Long>, TeamInvitationRepository

interface TeamInviteLinkJpaRepository : JpaRepository<TeamInviteLink, Long>, TeamInviteLinkRepository
