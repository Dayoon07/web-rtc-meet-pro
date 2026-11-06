package com.webrtcmeetpro.team.infrastructure

import com.webrtcmeetpro.team.domain.Team
import com.webrtcmeetpro.team.domain.TeamInvitation
import com.webrtcmeetpro.team.domain.TeamInvitationRepository
import com.webrtcmeetpro.team.domain.TeamMember
import com.webrtcmeetpro.team.domain.TeamMemberRepository
import com.webrtcmeetpro.team.domain.TeamRepository
import org.springframework.data.jpa.repository.JpaRepository

interface TeamJpaRepository : JpaRepository<Team, Long>, TeamRepository

interface TeamMemberJpaRepository : JpaRepository<TeamMember, Long>, TeamMemberRepository

interface TeamInvitationJpaRepository : JpaRepository<TeamInvitation, Long>, TeamInvitationRepository
