package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 글 보내기·고치기. 보낼 때만: replyToId = 답장할 메시지, mentionIds = @멘션한 사람, mentionAll = @모두, threadRootId = 스레드 답글 */
data class SendMessageReqDto(
    @field:NotBlank @field:Size(max = 4000) val content: String,
    val replyToId: Long? = null,
    @field:Size(max = 100) val mentionIds: List<Long> = emptyList(),
    val mentionAll: Boolean = false,
    val threadRootId: Long? = null,
)
