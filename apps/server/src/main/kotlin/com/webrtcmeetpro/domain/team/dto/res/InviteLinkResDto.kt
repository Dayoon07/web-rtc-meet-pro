package com.webrtcmeetpro.domain.team.dto.res

import java.time.LocalDateTime

data class InviteLinkResDto(
    val id: Long,
    val token: String,
    val hasPassword: Boolean,
    val joinCount: Int,
    val createdByName: String?,
    val createdAt: LocalDateTime,
)
