package com.webrtcmeetpro.domain.chat.dto.res

import com.webrtcmeetpro.domain.chat.MessageType
import java.time.LocalDateTime

data class ChatMessageResDto(
    val id: Long,
    val roomId: Long,
    val sender: ChatUserResDto?,
    val type: MessageType,
    val content: String?,
    val files: List<ChatFileResDto>,
    val createdAt: LocalDateTime,
    val edited: Boolean = false,
    /** 지운 메시지는 content·files 를 내려보내지 않는다 */
    val deleted: Boolean = false,
    val replyTo: ReplyPreviewResDto? = null,
    val reactions: List<ReactionResDto> = emptyList(),
    val mentions: List<Long> = emptyList(),
    val mentionAll: Boolean = false,
    val threadRootId: Long? = null,
    val replyCount: Int = 0,
    val lastReplyAt: LocalDateTime? = null,
    val threadUsers: List<ChatUserResDto> = emptyList(),
)
