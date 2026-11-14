package com.webrtcmeetpro.domain.team.dto.res

data class TeamDetailResDto(val team: TeamSummaryResDto, val members: List<TeamMemberResDto>, val limits: TeamLimitsResDto)
