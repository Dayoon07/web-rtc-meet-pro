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
    val memberCount: Int,
) {
    val canInvite get() = myRole == TeamRole.OWNER || myRole == TeamRole.ADMIN
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
