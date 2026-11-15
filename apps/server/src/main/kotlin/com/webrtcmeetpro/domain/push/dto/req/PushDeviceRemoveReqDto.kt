package com.webrtcmeetpro.domain.push.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PushDeviceRemoveReqDto(@field:NotBlank @field:Size(max = 500) val token: String)
