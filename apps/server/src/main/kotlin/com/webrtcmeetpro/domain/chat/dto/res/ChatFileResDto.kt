package com.webrtcmeetpro.domain.chat.dto.res

/** url 은 서명된 내려받기 주소 (12~24시간 유효). image = 화면에 바로 미리보기 가능한 이미지 */
data class ChatFileResDto(
    val id: Long,
    val name: String,
    val size: Long,
    val contentType: String,
    val image: Boolean,
    val url: String,
)
