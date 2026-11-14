package com.webrtcmeetpro.domain.chat.dto.res

import com.webrtcmeetpro.domain.chat.ChatRoomType
import java.time.LocalDateTime

data class ChatRoomResDto(
    val id: Long,
    val teamId: Long,
    val type: ChatRoomType,
    /** 채널의 카테고리(부서). null = 카테고리 없음 (1:1 방도 null) */
    val categoryId: Long? = null,
    /** 채널 음성방에 지금 들어와 있는 사람 (없으면 null) */
    val voice: VoicePresenceResDto? = null,
    /** GROUP 방 이름. DIRECT 는 null → 클라이언트가 상대방 닉네임을 표시 */
    val name: String?,
    val createdBy: Long,
    val members: List<ChatUserResDto>,
    val lastMessage: ChatMessageResDto?,
    val unreadCount: Long,
    val lastMessageAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    /** 방 공지 (없으면 null) */
    val notice: ChatNoticeResDto? = null,
    /** 방 사람마다 마지막으로 읽은 메시지 (메시지 옆 안 읽은 사람 수) */
    val reads: List<ReadStateResDto> = emptyList(),
    /** 내가 이 방 알림을 껐는지 */
    val muted: Boolean = false,
)
