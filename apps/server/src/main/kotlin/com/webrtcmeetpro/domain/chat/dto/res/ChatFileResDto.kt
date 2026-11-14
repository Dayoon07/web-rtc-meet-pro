package com.webrtcmeetpro.domain.chat.dto.res

/** url 은 서명된 주소라 12~24시간 뒤 만료된다 */
data class ChatFileResDto(
    val id: Long,
    val name: String,
    val size: Long,
    val contentType: String,
    val image: Boolean,
    val url: String,
)
