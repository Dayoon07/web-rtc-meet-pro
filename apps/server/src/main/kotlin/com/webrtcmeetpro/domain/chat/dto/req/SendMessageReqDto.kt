package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 고칠 때는 content 만 쓴다 */
data class SendMessageReqDto(
    @field:NotBlank @field:Size(max = 4000) val content: String,
    val replyToId: Long? = null,
    @field:Size(max = 100) val mentionIds: List<Long> = emptyList(),
    val mentionAll: Boolean = false,
    val threadRootId: Long? = null,
)
