package com.webrtcmeetpro.domain.team.dto.res

/** 한도는 소유자의 지금 플랜 기준이고, 인원은 소유자의 모든 팀 합계다. maxGroupRooms 는 null 이면 제한 없음 */
data class TeamLimitsResDto(
    val maxMembers: Int,
    val maxGroupRooms: Int?,
    val pendingInvitations: Int,
    val maxMeetingParticipants: Int = 0,
    val recordingEnabled: Boolean = false,
    val planCode: String = "",
    val overageAllowed: Boolean = false,
    val overagePrice: Long = 0,
    val usedMembers: Int = 0,
    val ownedTeamCount: Int = 1,
)
