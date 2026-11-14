package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ReactionReqDto(@field:NotBlank @field:Size(max = 32) val emoji: String)
