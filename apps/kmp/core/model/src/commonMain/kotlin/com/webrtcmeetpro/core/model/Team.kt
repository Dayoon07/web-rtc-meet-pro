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
)
