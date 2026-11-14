package com.webrtcmeetpro.domain.chat.dto.res

/** MESSAGE_UPDATED 는 고치거나 지운 메시지라 화면이 같은 id 를 바꿔 끼운다 */
data class ChatEventResDto(
    val type: String,
    val teamId: Long,
    val roomId: Long,
    val message: ChatMessageResDto? = null,
    val read: ReadStateResDto? = null,
    val typing: ChatUserResDto? = null,
)
