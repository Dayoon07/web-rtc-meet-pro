package com.webrtcmeetpro.domain.team.dto.res

data class InviteLinkPreviewResDto(
    val teamId: Long,
    val teamName: String,
    val teamDescription: String?,
    val memberCount: Int,
    val hasPassword: Boolean,
    /** true 면 수락 대신 채팅으로 보낸다 */
    val alreadyMember: Boolean,
)
