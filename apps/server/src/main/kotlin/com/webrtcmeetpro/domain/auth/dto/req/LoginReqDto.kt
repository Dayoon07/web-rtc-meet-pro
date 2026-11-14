package com.webrtcmeetpro.domain.auth.dto.req

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginReqDto(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
)
