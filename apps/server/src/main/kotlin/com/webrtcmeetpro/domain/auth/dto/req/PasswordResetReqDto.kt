package com.webrtcmeetpro.domain.auth.dto.req

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PasswordResetReqDto(@field:NotBlank @field:Email @field:Size(max = 255) val email: String)
