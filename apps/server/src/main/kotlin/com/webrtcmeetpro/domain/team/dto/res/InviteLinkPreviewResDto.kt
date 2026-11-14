package com.webrtcmeetpro.domain.team.dto.res

/** 링크로 들어온 사람이 보는 팀 소개 */
data class InviteLinkPreviewResDto(
    val teamId: Long,
    val teamName: String,
    val teamDescription: String?,
    val memberCount: Int,
    val hasPassword: Boolean,
    /** 이미 이 팀 멤버면 수락 대신 "채팅으로 가기" */
    val alreadyMember: Boolean,
)
