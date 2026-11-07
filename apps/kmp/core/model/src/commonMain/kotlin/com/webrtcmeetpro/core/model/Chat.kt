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
)

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
) {
    /** 목록·상단에 보여 줄 이름: 그룹은 방 이름, 1:1 은 상대 닉네임 */
    fun title(myId: Long): String =
        name ?: members.firstOrNull { it.id != myId }?.nickname ?: members.firstOrNull()?.nickname.orEmpty()

    fun partner(myId: Long): ChatUser? = if (type == ChatRoomType.DIRECT) members.firstOrNull { it.id != myId } else null
}

/** STOMP /user/queue/chat. type: MESSAGE(새 메시지) | MESSAGE_UPDATED(고치거나 지운 메시지) | ROOM(방 목록 갱신: 새 방·초대·퇴장) */
@Serializable
data class ChatEvent(val type: String, val teamId: Long, val roomId: Long, val message: ChatMessage? = null)

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

@Serializable
data class SendMessageRequest(val content: String)

@Serializable
data class MarkReadRequest(val messageId: Long)

@Serializable
data class CountResponse(val count: Long)
