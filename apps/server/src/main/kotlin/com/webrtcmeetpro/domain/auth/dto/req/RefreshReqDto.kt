package com.webrtcmeetpro.domain.auth.dto.req

import jakarta.validation.constraints.NotBlank

data class RefreshReqDto(@field:NotBlank val refreshToken: String)
