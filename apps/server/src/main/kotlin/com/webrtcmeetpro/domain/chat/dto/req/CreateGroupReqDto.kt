package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateGroupReqDto(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:Size(max = 200) val memberIds: List<Long> = emptyList(),
    val categoryId: Long? = null,
)
