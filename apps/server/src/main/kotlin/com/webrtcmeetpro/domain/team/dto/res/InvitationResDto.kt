package com.webrtcmeetpro.domain.team.dto.res

import com.webrtcmeetpro.domain.team.InvitationStatus
import java.time.LocalDateTime

data class InvitationResDto(
    val id: Long,
    val teamId: Long,
    val teamName: String,
    val inviterName: String,
    val inviteeEmail: String,
    val status: InvitationStatus,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
)
