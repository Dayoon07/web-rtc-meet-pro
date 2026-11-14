package com.webrtcmeetpro.domain.team.dto.req

import com.webrtcmeetpro.domain.team.TeamRole
import jakarta.validation.constraints.NotNull

/** ADMIN 또는 MEMBER (OWNER 는 줄 수 없다) */
data class ChangeRoleReqDto(@field:NotNull val role: TeamRole?)
