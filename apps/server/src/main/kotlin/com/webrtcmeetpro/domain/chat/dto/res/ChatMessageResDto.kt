package com.webrtcmeetpro.domain.chat.dto.res

import com.webrtcmeetpro.domain.chat.MessageType
import java.time.LocalDateTime

data class ChatMessageResDto(
    val id: Long,
    val roomId: Long,
    val sender: ChatUserResDto?,
    val type: MessageType,
    /** FILE 메시지는 글 없이 파일만 보내면 null */
    val content: String?,
    val files: List<ChatFileResDto>,
    val createdAt: LocalDateTime,
    /** 보낸 사람이 고친 글 ("수정됨") */
    val edited: Boolean = false,
    /** 지운 메시지: content 는 null, files 는 비어 있다 ("삭제된 메시지예요") */
    val deleted: Boolean = false,
    /** 답장이면 원래 메시지 미리보기 */
    val replyTo: ReplyPreviewResDto? = null,
    /** 이모지 반응 (먼저 단 이모지부터) */
    val reactions: List<ReactionResDto> = emptyList(),
    /** @멘션한 사람 user_id */
    val mentions: List<Long> = emptyList(),
    /** @모두 */
    val mentionAll: Boolean = false,
    /** 스레드 답글이면 스레드 시작 글 id (채널 본문이 아니라 스레드 창에 넣는다) */
    val threadRootId: Long? = null,
    /** 스레드 시작 글: 답글 수 · 마지막 답글 시각 · 답글 단 사람(최근 순 몇 명) */
    val replyCount: Int = 0,
    val lastReplyAt: LocalDateTime? = null,
    val threadUsers: List<ChatUserResDto> = emptyList(),
)
