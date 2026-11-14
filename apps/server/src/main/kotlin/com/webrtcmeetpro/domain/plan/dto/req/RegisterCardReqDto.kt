package com.webrtcmeetpro.domain.plan.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterCardReqDto(
    @field:NotBlank @field:Size(max = 50) val customerKey: String,
    @field:NotBlank @field:Size(max = 300) val authKey: String,
)
