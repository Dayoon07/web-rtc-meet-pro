package com.webrtcmeetpro.domain.user.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 비밀번호 바꾸기. 비밀번호가 없는(구글 전용) 계정은 currentPassword 를 비워 둔다 */
data class ChangePasswordReqDto(
    @field:Size(max = 72) val currentPassword: String? = null,
    @field:NotBlank @field:Size(min = 8, max = 72) val newPassword: String,
)
