package com.webrtcmeetpro.domain.chat.dto.res

import com.webrtcmeetpro.domain.chat.MessageType

data class ReplyPreviewResDto(val id: Long, val sender: ChatUserResDto?, val content: String?, val type: MessageType, val fileCount: Int, val deleted: Boolean)
