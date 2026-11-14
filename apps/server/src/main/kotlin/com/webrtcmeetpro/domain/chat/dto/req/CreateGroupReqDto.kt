package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateGroupReqDto(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:Size(max = 200) val memberIds: List<Long> = emptyList(),
    /** 넣을 카테고리(부서). 없으면 카테고리 밖 */
    val categoryId: Long? = null,
)
