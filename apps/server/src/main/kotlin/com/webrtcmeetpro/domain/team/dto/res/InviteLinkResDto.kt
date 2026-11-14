package com.webrtcmeetpro.domain.team.dto.res

import java.time.LocalDateTime

/** 팀 화면 "멤버 초대" 탭의 링크 한 줄 (팀 소유자·관리자만 본다) */
data class InviteLinkResDto(
    val id: Long,
    val token: String,
    val hasPassword: Boolean,
    val joinCount: Int,
    val createdByName: String?,
    val createdAt: LocalDateTime,
)
