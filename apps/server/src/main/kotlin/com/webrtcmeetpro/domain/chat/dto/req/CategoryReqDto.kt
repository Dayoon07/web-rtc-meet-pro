package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CategoryReqDto(@field:NotBlank @field:Size(max = 50) val name: String)
