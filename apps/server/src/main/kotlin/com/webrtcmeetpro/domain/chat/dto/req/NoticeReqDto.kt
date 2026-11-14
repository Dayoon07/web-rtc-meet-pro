package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.Size

/** content 가 비면 공지를 내린다 */
data class NoticeReqDto(@field:Size(max = 1000) val content: String? = null)
