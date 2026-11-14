package com.webrtcmeetpro.domain.chat.dto.res

import com.webrtcmeetpro.domain.chat.MessageType

/** 답장한 원래 메시지 미리보기 (지운 메시지면 content 없이 deleted) */
data class ReplyPreviewResDto(val id: Long, val sender: ChatUserResDto?, val content: String?, val type: MessageType, val fileCount: Int, val deleted: Boolean)
