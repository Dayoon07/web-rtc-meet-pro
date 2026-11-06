package com.webrtcmeetpro.chat.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class ChatRoomType { DIRECT, GROUP }

/**
 * 팀 안의 채팅방.
 * - DIRECT: 팀원 두 명의 1:1 방. 팀원이면 누구나 열 수 있고 같은 두 사람의 방은 하나뿐(directKey)
 * - GROUP : 1:N 방. 팀 OWNER 만 만들 수 있고, 방 참여자가 다른 팀원을 초대할 수 있다
 */
@Entity
@Table(name = "chat_rooms")
class ChatRoom protected constructor(
    @Column(nullable = false)
    val teamId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val roomType: ChatRoomType,

    var name: String?,

    val directKey: String?,

    @Column(nullable = false)
    val createdBy: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    var id: Long? = null
        protected set

    var lastMessageAt: LocalDateTime? = null
        protected set

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()

    var deletedAt: LocalDateTime? = null
        protected set

    val isGroup: Boolean get() = roomType == ChatRoomType.GROUP

    fun touch(at: LocalDateTime = LocalDateTime.now()) {
        lastMessageAt = at
    }

    companion object {
        fun directKeyOf(a: Long, b: Long) = "${minOf(a, b)}_${maxOf(a, b)}"

        fun direct(teamId: Long, creatorId: Long, otherId: Long) =
            ChatRoom(teamId, ChatRoomType.DIRECT, null, directKeyOf(creatorId, otherId), creatorId)

        fun group(teamId: Long, creatorId: Long, name: String) =
            ChatRoom(teamId, ChatRoomType.GROUP, name.trim().take(100), null, creatorId)
    }
}

interface ChatRoomRepository {
    fun save(room: ChatRoom): ChatRoom
    fun findRoomById(id: Long): ChatRoom?
    fun findByTeamIdAndDirectKey(teamId: Long, directKey: String): ChatRoom?
    fun findAllByIdInAndDeletedAtIsNull(ids: Collection<Long>): List<ChatRoom>
    fun countByTeamIdAndRoomTypeAndDeletedAtIsNull(teamId: Long, roomType: ChatRoomType): Long
}

@Entity
@Table(name = "chat_room_members")
class ChatRoomMember(
    @Column(nullable = false)
    val roomId: Long,

    @Column(nullable = false)
    val userId: Long,

    val invitedBy: Long? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_member_id")
    var id: Long? = null
        protected set

    var lastReadMessageId: Long? = null
        protected set

    @Column(nullable = false)
    var joinedAt: LocalDateTime = LocalDateTime.now()
        protected set

    var leftAt: LocalDateTime? = null
        protected set

    val isActive: Boolean get() = leftAt == null

    fun markRead(messageId: Long) {
        if ((lastReadMessageId ?: 0) < messageId) lastReadMessageId = messageId
    }

    fun leave(at: LocalDateTime = LocalDateTime.now()) {
        leftAt = at
    }

    fun rejoin(at: LocalDateTime = LocalDateTime.now()) {
        leftAt = null
        joinedAt = at
    }
}

interface ChatRoomMemberRepository {
    fun save(member: ChatRoomMember): ChatRoomMember
    fun findByRoomIdAndUserId(roomId: Long, userId: Long): ChatRoomMember?
    fun findAllByRoomIdAndLeftAtIsNull(roomId: Long): List<ChatRoomMember>
    fun findAllByUserIdAndLeftAtIsNull(userId: Long): List<ChatRoomMember>
}

enum class MessageType { TEXT, SYSTEM }

@Entity
@Table(name = "chat_messages")
class ChatMessage(
    @Column(nullable = false)
    val roomId: Long,

    /** SYSTEM 메시지는 null */
    val senderId: Long?,

    /** SYSTEM 메시지는 번역 키 + 파라미터 JSON (예: {"key":"invited","by":"민지","names":["서준"]}) */
    @Column(nullable = false, length = 4000)
    var content: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val messageType: MessageType = MessageType.TEXT,

    val meetingId: Long? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()

    var editedAt: LocalDateTime? = null
        protected set

    var deletedAt: LocalDateTime? = null
        protected set
}

interface ChatMessageRepository {
    fun save(message: ChatMessage): ChatMessage
    /** 커서 페이지네이션: beforeId 보다 오래된 메시지 최신순 */
    fun findTop50ByRoomIdAndIdLessThanOrderByIdDesc(roomId: Long, beforeId: Long): List<ChatMessage>
    fun findTop50ByRoomIdOrderByIdDesc(roomId: Long): List<ChatMessage>
    fun findTopByRoomIdOrderByIdDesc(roomId: Long): ChatMessage?
    fun countByRoomIdAndIdGreaterThan(roomId: Long, afterId: Long): Long
}
