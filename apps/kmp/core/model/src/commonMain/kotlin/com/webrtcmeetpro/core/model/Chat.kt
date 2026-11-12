package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChatRoomType { DIRECT, GROUP }

/** TEXT 글 / FILE 첨부파일(+선택 글) / SYSTEM 입장·퇴장 안내 */
@Serializable
enum class MessageType { TEXT, FILE, SYSTEM }

/** url: 서버가 서명해 준 내려받기 주소 (상대 경로, 12~24시간 유효). image: 바로 미리보기 가능한 이미지 */
@Serializable
data class ChatFile(
    val id: Long,
    val name: String,
    val size: Long,
    val contentType: String,
    val image: Boolean = false,
    val url: String,
)

@Serializable
data class ChatUser(val id: Long, val nickname: String, val profileImageUrl: String? = null)

@Serializable
data class ChatMessage(
    val id: Long,
    val roomId: Long,
    /** SYSTEM 메시지(입장·퇴장 등)는 null */
    val sender: ChatUser? = null,
    val type: MessageType = MessageType.TEXT,
    /** FILE 메시지는 글 없이 파일만 보내면 null */
    val content: String? = null,
    val files: List<ChatFile> = emptyList(),
    val createdAt: String,
    /** 보낸 사람이 고친 글 ("수정됨") */
    val edited: Boolean = false,
    /** 지운 메시지: content 는 null, files 는 비어 있다 */
    val deleted: Boolean = false,
    /** 답장이면 원래 메시지 미리보기 */
    val replyTo: ReplyPreview? = null,
    /** 이모지 반응 (먼저 단 것부터) */
    val reactions: List<Reaction> = emptyList(),
    /** @멘션한 사람 */
    val mentions: List<Long> = emptyList(),
    /** @모두 */
    val mentionAll: Boolean = false,
)

/** 답장한 원래 메시지 (지웠으면 content 없이 deleted) */
@Serializable
data class ReplyPreview(
    val id: Long,
    val sender: ChatUser? = null,
    val content: String? = null,
    val type: MessageType = MessageType.TEXT,
    val fileCount: Int = 0,
    val deleted: Boolean = false,
)

/** 이모지 하나에 단 사람들 */
@Serializable
data class Reaction(val emoji: String, val count: Int, val userIds: List<Long> = emptyList())

/** 방 사람이 어디까지 읽었는지 (메시지 옆 안 읽은 사람 수) */
@Serializable
data class ReadState(val userId: Long, val lastReadMessageId: Long? = null)

/** 방 공지 (방 위쪽에 고정) */
@Serializable
data class ChatNotice(val content: String, val by: ChatUser? = null, val at: String? = null)

/** 채널 음성방: 진행 중인 회의와 들어와 있는 사람 */
@Serializable
data class VoicePresence(val meetingId: Long, val participants: List<ChatUser> = emptyList())

/** 채널을 묶는 카테고리 = 부서 */
@Serializable
data class ChatCategory(val id: Long, val name: String, val sortOrder: Int = 0)

@Serializable
data class ChatRoom(
    val id: Long,
    val teamId: Long,
    val type: ChatRoomType,
    /** GROUP(채널) 이름. DIRECT 는 null → 상대방 닉네임을 보여 준다 */
    val name: String? = null,
    /** 채널의 카테고리(부서). null = 카테고리 없음 */
    val categoryId: Long? = null,
    /** 채널 음성방에 지금 들어와 있는 사람 (없으면 null) */
    val voice: VoicePresence? = null,
    val createdBy: Long,
    val members: List<ChatUser> = emptyList(),
    val lastMessage: ChatMessage? = null,
    val unreadCount: Long = 0,
    val lastMessageAt: String? = null,
    val createdAt: String,
    /** 방 공지 (없으면 null) */
    val notice: ChatNotice? = null,
    /** 방 사람마다 마지막으로 읽은 메시지 */
    val reads: List<ReadState> = emptyList(),
    /** 내가 이 방 알림을 껐는지 */
    val muted: Boolean = false,
) {
    /** 목록·상단에 보여 줄 이름: 그룹은 방 이름, 1:1 은 상대 닉네임 */
    fun title(myId: Long): String =
        name ?: members.firstOrNull { it.id != myId }?.nickname ?: members.firstOrNull()?.nickname.orEmpty()

    fun partner(myId: Long): ChatUser? = if (type == ChatRoomType.DIRECT) members.firstOrNull { it.id != myId } else null
}

/**
 * STOMP /user/queue/chat. type: MESSAGE(새 메시지) | MESSAGE_UPDATED(고치거나 지운 메시지) | ROOM(방 목록 갱신)
 * | READ(누가 어디까지 읽음) | TYPING(입력 중) | CATEGORIES
 */
@Serializable
data class ChatEvent(
    val type: String,
    val teamId: Long,
    val roomId: Long,
    val message: ChatMessage? = null,
    val read: ReadState? = null,
    val typing: ChatUser? = null,
)

/** 접속 상태: ONLINE 접속 중 · AWAY 자리 비움 · OFFLINE 접속 안 함 (/user/queue/presence) */
@Serializable
enum class PresenceStatus { ONLINE, AWAY, OFFLINE }

@Serializable
data class PresenceView(val userId: Long, val status: PresenceStatus)

@Serializable
data class OpenDirectRequest(val userId: Long)

@Serializable
data class CreateGroupRequest(val name: String, val memberIds: List<Long>, val categoryId: Long? = null)

/** 채널 고치기: name 이 있으면 이름 바꾸기, moveCategory=true 면 categoryId 로 옮기기 (null = 카테고리 밖) */
@Serializable
data class UpdateChannelRequest(val name: String? = null, val categoryId: Long? = null, val moveCategory: Boolean = false)

@Serializable
data class CategoryRequest(val name: String)

@Serializable
data class InviteMembersRequest(val userIds: List<Long>)

/** 보낼 때만: replyToId = 답장할 메시지, mentionIds = @멘션한 사람, mentionAll = @모두 */
@Serializable
data class SendMessageRequest(
    val content: String,
    val replyToId: Long? = null,
    val mentionIds: List<Long> = emptyList(),
    val mentionAll: Boolean = false,
)

@Serializable
data class ReactionRequest(val emoji: String)

/** content 가 null 이면 공지 내리기 */
@Serializable
data class NoticeRequest(val content: String?)

@Serializable
data class MuteRequest(val muted: Boolean)

@Serializable
data class MarkReadRequest(val messageId: Long)

@Serializable
data class CountResponse(val count: Long)
