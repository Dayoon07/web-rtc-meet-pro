package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.Size

/** categoryId 가 null 이면 카테고리 밖으로 꺼내는 것이라, 옮기려는 건지 moveCategory 로 따로 받는다 */
data class UpdateChannelReqDto(
    @field:Size(max = 100) val name: String? = null,
    val categoryId: Long? = null,
    val moveCategory: Boolean = false,
)
