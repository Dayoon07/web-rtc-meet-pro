package com.webrtcmeetpro.domain.admin.dto.res

import java.time.LocalDateTime

data class PendingSignupResDto(
    val id: Long,
    val email: String,
    val nickname: String,
    val realName: String?,
    /** false 면 구글로 가입한 계정 */
    val hasPassword: Boolean,
    val createdAt: LocalDateTime,
    val lastLoginAt: LocalDateTime?,
)
