package com.webrtcmeetpro.domain.push.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PushUnsubscribeReqDto(@field:NotBlank @field:Size(max = 1000) val endpoint: String)
