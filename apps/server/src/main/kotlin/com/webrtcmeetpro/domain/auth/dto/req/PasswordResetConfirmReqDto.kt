package com.webrtcmeetpro.domain.auth.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PasswordResetConfirmReqDto(
    @field:NotBlank @field:Size(max = 500) val token: String,
    @field:NotBlank @field:Size(min = 8, max = 72) val password: String,
)
