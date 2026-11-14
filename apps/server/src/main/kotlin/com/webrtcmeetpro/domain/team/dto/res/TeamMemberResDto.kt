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
    /** 이 화면을 보는 사람이 이 팀원을 내보낼 수 있는지 (OWNER: 누구나, ADMIN: 일반 팀원만) */
    val removable: Boolean = false,
)
