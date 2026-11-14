package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.Size

/** 채널 고치기: name 이 있으면 이름 바꾸기, moveCategory=true 면 categoryId 로 옮기기 (null = 카테고리 밖) */
data class UpdateChannelReqDto(
    @field:Size(max = 100) val name: String? = null,
    val categoryId: Long? = null,
    val moveCategory: Boolean = false,
)
