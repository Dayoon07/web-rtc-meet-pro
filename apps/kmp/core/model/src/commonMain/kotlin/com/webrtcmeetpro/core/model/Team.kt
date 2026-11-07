package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TeamRole { OWNER, ADMIN, MEMBER }

/** GET /api/teams */
@Serializable
data class TeamSummary(
    val id: Long,
    val name: String,
    val description: String? = null,
    val ownerId: Long,
    val myRole: TeamRole,
    /**
     * 내 역할로 이 팀에서 할 수 있는 일 (서버 TeamPermission 이름). 어느 역할이 무엇을 할 수 있는지는 서버만 안다.
     * 서버가 권한을 새로 추가해도 앱이 깨지지 않게 enum 이 아니라 문자열로 받는다
     */
    val myPermissions: List<String> = emptyList(),
    val memberCount: Int,
) {
    val canInvite get() = "INVITE_MEMBER" in myPermissions
    val canCreateGroupRoom get() = "CREATE_GROUP_ROOM" in myPermissions
    /** 팀원 역할 바꾸기 (ADMIN 지정·해제). 지금은 팀 OWNER */
    val canManageRoles get() = "MANAGE_ROLES" in myPermissions
}

@Serializable
data class TeamMember(
    val userId: Long,
    val nickname: String,
    val email: String,
    val profileImageUrl: String? = null,
    val role: TeamRole,
    val joinedAt: String,
)

@Serializable
data class TeamLimits(val maxMembers: Int, val maxGroupRooms: Int, val pendingInvitations: Int)

/** GET /api/teams/{id} */
@Serializable
data class TeamDetail(val team: TeamSummary, val members: List<TeamMember>, val limits: TeamLimits)

@Serializable
enum class InvitationStatus { PENDING, ACCEPTED, DECLINED, CANCELED, EXPIRED }

@Serializable
data class Invitation(
    val id: Long,
    val teamId: Long,
    val teamName: String,
    val inviterName: String,
    val inviteeEmail: String,
    val status: InvitationStatus,
    val expiresAt: String,
    val createdAt: String,
)

@Serializable
data class CreateTeamRequest(val name: String, val description: String? = null)

@Serializable
data class InviteRequest(val email: String)

/** PATCH /api/teams/{teamId}/members/{userId}/role. ADMIN 또는 MEMBER (OWNER 는 줄 수 없다) */
@Serializable
data class ChangeRoleRequest(val role: TeamRole)
