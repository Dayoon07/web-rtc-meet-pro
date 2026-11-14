package com.webrtcmeetpro.domain.team.dto.res

import com.webrtcmeetpro.domain.team.TeamPermission
import com.webrtcmeetpro.domain.team.TeamRole
import java.time.LocalDateTime

data class TeamSummaryResDto(
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
