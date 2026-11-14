package com.webrtcmeetpro.domain.team.dto.res

import com.webrtcmeetpro.domain.team.TeamRole
import java.time.LocalDateTime

data class TeamMemberResDto(
    val userId: Long,
    val nickname: String,
    val email: String,
    val profileImageUrl: String?,
    val role: TeamRole,
    val joinedAt: LocalDateTime,
    val removable: Boolean = false,
)
