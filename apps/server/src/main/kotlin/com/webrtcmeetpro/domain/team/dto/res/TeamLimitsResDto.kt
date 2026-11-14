package com.webrtcmeetpro.domain.team.dto.res

/** maxGroupRooms: null = 제한 없음. 한도는 팀 소유자의 지금 플랜 기준 */
/** 한도 (인원은 소유자의 모든 팀 합계: 여러 팀에 있는 사람은 한 명) */
data class TeamLimitsResDto(
    val maxMembers: Int,
    val maxGroupRooms: Int?,
    /** 소유자의 모든 팀에 대기 중인 초대 */
    val pendingInvitations: Int,
    val maxMeetingParticipants: Int = 0,
    val recordingEnabled: Boolean = false,
    val planCode: String = "",
    /** 소유자가 한도 초과를 허용함 (넘게 초대할 수 있고, 1명당 월 overagePrice 원) */
    val overageAllowed: Boolean = false,
    val overagePrice: Long = 0,
    /** 소유자의 모든 팀 사람 수 */
    val usedMembers: Int = 0,
    /** 소유자가 만든 팀 수 */
    val ownedTeamCount: Int = 1,
)
