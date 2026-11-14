package com.webrtcmeetpro.domain.auth.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EmailVerificationConfirmReqDto(@field:NotBlank @field:Size(max = 500) val token: String)
