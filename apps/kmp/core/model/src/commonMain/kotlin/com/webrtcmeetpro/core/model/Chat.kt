package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChatRoomType { DIRECT, GROUP }

@Serializable
enum class MessageType { TEXT, SYSTEM }

@Serializable
data class ChatUser(val id: Long, val nickname: String, val profileImageUrl: String? = null)

@Serializable
data class ChatMessage(
    val id: Long,
    val roomId: Long,
    /** SYSTEM 메시지(입장·퇴장 등)는 null */
    val sender: ChatUser? = null,
    val type: MessageType = MessageType.TEXT,
    val content: String,
    val createdAt: String,
)

@Serializable
data class ChatRoom(
    val id: Long,
    val teamId: Long,
    val type: ChatRoomType,
    /** GROUP 방 이름. DIRECT 는 null → 상대방 닉네임을 보여 준다 */
    val name: String? = null,
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

/** STOMP /user/queue/chat. type: MESSAGE(새 메시지) | ROOM(방 목록 갱신: 새 방·초대·퇴장) */
@Serializable
data class ChatEvent(val type: String, val teamId: Long, val roomId: Long, val message: ChatMessage? = null)

@Serializable
data class OpenDirectRequest(val userId: Long)

@Serializable
data class CreateGroupRequest(val name: String, val memberIds: List<Long>)

@Serializable
data class InviteMembersRequest(val userIds: List<Long>)

@Serializable
data class SendMessageRequest(val content: String)

@Serializable
data class MarkReadRequest(val messageId: Long)

@Serializable
data class CountResponse(val count: Long)
