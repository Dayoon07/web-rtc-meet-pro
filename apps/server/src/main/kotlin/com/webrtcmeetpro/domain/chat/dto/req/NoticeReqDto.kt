package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.Size

/** 방 공지. content 가 비었거나 null 이면 내리기 */
data class NoticeReqDto(@field:Size(max = 1000) val content: String? = null)
