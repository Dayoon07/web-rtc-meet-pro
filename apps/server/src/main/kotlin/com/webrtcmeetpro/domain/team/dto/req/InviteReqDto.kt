package com.webrtcmeetpro.domain.team.dto.req

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class InviteReqDto(@field:NotBlank @field:Email @field:Size(max = 255) val email: String)
