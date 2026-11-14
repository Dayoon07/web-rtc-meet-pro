package com.webrtcmeetpro.domain.auth.dto.req

import jakarta.validation.constraints.NotBlank

data class OAuthExchangeReqDto(@field:NotBlank val code: String)
