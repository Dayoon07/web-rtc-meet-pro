package com.webrtcmeetpro.domain.chat.dto.res

import java.time.LocalDateTime

data class ChatNoticeResDto(val content: String, val by: ChatUserResDto?, val at: LocalDateTime?)
