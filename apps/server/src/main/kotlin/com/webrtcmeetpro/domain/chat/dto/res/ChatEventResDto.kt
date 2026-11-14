package com.webrtcmeetpro.domain.chat.dto.res

/** type: MESSAGE, MESSAGE_UPDATED, ROOM(방 목록 다시), CATEGORIES(카테고리 다시), READ(누가 어디까지 읽음) */
data class ChatEventResDto(
    val type: String,
    val teamId: Long,
    val roomId: Long,
    val message: ChatMessageResDto? = null,
    val read: ReadStateResDto? = null,
    /** TYPING: 입력 중인 사람 */
    val typing: ChatUserResDto? = null,
)
