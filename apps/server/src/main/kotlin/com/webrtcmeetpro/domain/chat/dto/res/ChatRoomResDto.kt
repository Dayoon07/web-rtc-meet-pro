package com.webrtcmeetpro.domain.chat.dto.res

import com.webrtcmeetpro.domain.chat.ChatRoomType
import java.time.LocalDateTime

data class ChatRoomResDto(
    val id: Long,
    val teamId: Long,
    val type: ChatRoomType,
    val categoryId: Long? = null,
    val voice: VoicePresenceResDto? = null,
    /** DIRECT 는 null 이라 화면이 상대 닉네임을 쓴다 */
    val name: String?,
    val createdBy: Long,
    val members: List<ChatUserResDto>,
    val lastMessage: ChatMessageResDto?,
    val unreadCount: Long,
    val lastMessageAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val notice: ChatNoticeResDto? = null,
    /** 메시지 옆 안 읽은 사람 수 계산용 */
    val reads: List<ReadStateResDto> = emptyList(),
    val muted: Boolean = false,
)
