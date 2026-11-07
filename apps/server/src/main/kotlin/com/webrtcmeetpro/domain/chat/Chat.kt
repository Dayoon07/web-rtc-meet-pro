package com.webrtcmeetpro.domain.chat

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.io.InputStream
import java.nio.file.Path
import java.time.LocalDateTime

enum class ChatRoomType { DIRECT, GROUP }

/**
 * 워크스페이스(팀) 안의 채팅방.
 * - DIRECT: 멤버 두 명의 1:1 방. 멤버면 누구나 열 수 있고 같은 두 사람의 방은 하나뿐(directKey)
 * - GROUP : 채널 (부서별 팀 등). OWNER·ADMIN 이 만들고 카테고리(부서)에 넣을 수 있다. 방 참여자가 다른 멤버를 초대할 수 있다
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
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    var id: Long? = null
        protected set

    var lastMessageAt: LocalDateTime? = null
        protected set

    /** 채널이 들어 있는 카테고리(부서). null = 카테고리 없음 */
    var categoryId: Long? = null
        protected set

    @Column(nullable = false)
    var sortOrder: Int = 0
        protected set

    val isGroup: Boolean get() = roomType == ChatRoomType.GROUP

    fun rename(name: String) {
        check(isGroup) { "1:1 방은 이름이 없습니다." }
        this.name = name.trim().take(100)
    }

    fun moveTo(categoryId: Long?) {
        check(isGroup) { "1:1 방은 카테고리에 넣을 수 없습니다." }
        this.categoryId = categoryId
    }

    fun touch(at: LocalDateTime = LocalDateTime.now()) {
        lastMessageAt = at
    }

    /** 팀을 삭제하면 방도 사용 안 함 (행은 남긴다) */
    fun delete() = markDeleted()

    companion object {
        fun directKeyOf(a: Long, b: Long) = "${minOf(a, b)}_${maxOf(a, b)}"

        fun direct(teamId: Long, creatorId: Long, otherId: Long) =
            ChatRoom(teamId, ChatRoomType.DIRECT, null, directKeyOf(creatorId, otherId), creatorId)

        fun group(teamId: Long, creatorId: Long, name: String) =
            ChatRoom(teamId, ChatRoomType.GROUP, name.trim().take(100), null, creatorId)
    }
}

/** 채널을 묶는 카테고리 = 부서. 예) "개발 연구소" 안에 #백오피스, #프론트오피스 */
@Entity
@Table(name = "chat_categories")
class ChatCategory(
    @Column(nullable = false)
    val teamId: Long,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var sortOrder: Int = 0,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    var id: Long? = null
        protected set

    fun rename(name: String) {
        this.name = name.trim().take(50)
    }

    fun delete() = markDeleted()
}

interface ChatCategoryRepository {
    fun save(category: ChatCategory): ChatCategory
    fun findCategoryById(id: Long): ChatCategory?
    fun findAllByTeamIdAndDeletedFalseOrderBySortOrderAscIdAsc(teamId: Long): List<ChatCategory>
}

interface ChatRoomRepository {
    fun save(room: ChatRoom): ChatRoom
    fun findRoomById(id: Long): ChatRoom?
    fun findByTeamIdAndDirectKey(teamId: Long, directKey: String): ChatRoom?
    fun findAllByIdInAndDeletedFalse(ids: Collection<Long>): List<ChatRoom>
    fun countByTeamIdAndRoomTypeAndDeletedFalse(teamId: Long, roomType: ChatRoomType): Long
    fun findAllByTeamIdAndDeletedFalse(teamId: Long): List<ChatRoom>
}

@Entity
@Table(name = "chat_room_members")
class ChatRoomMember(
    @Column(nullable = false)
    val roomId: Long,

    @Column(nullable = false)
    val userId: Long,

    val invitedBy: Long? = null,
) : BaseEntity() {
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

/**
 * TEXT  : 글만
 * FILE  : 첨부파일([ChatMessageFile]) + 함께 보낸 글(없으면 null)
 * SYSTEM: 초대/퇴장 안내. 보낸 사람이 없고 content 는 번역 키 + 파라미터 JSON
 */
enum class MessageType { TEXT, FILE, SYSTEM }

@Entity
@Table(name = "chat_messages")
class ChatMessage(
    @Column(nullable = false)
    val roomId: Long,

    /** SYSTEM 메시지는 null */
    val senderId: Long?,

    /**
     * FILE 메시지는 null 일 수 있다. SYSTEM 메시지는 번역 키 + 파라미터 JSON (예: {"key":"invited","actor":"민지","targets":["서준"]}).
     * CLOB: 한글 4000자는 VARCHAR2(4000바이트)에 들어가지 않는다
     */
    @Lob
    @Column(name = "content")
    var content: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val messageType: MessageType = MessageType.TEXT,

    val meetingId: Long? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    var id: Long? = null
        protected set

    /** 보낸 사람이 글을 고친 시각 (화면에 "수정됨"). 공통 컬럼 edited_dt 는 삭제 표시에도 바뀌어서 따로 둔다 */
    var contentEditedAt: LocalDateTime? = null
        protected set

    init {
        require(messageType == MessageType.FILE || content != null) { "$messageType 메시지는 content 가 필요합니다." }
    }

    /** 글 고치기 (TEXT 만. 파일 메시지의 글·시스템 메시지는 고치지 않는다) */
    fun editContent(newContent: String, at: LocalDateTime = LocalDateTime.now()) {
        check(messageType == MessageType.TEXT && !deleted) { "글 메시지만 고칠 수 있습니다." }
        content = newContent
        contentEditedAt = at
    }

    /** 지우기: 행은 남기고(답장·읽음 위치가 가리킨다) 화면에는 "삭제된 메시지"로 보인다 */
    fun delete() = markDeleted()
}

interface ChatMessageRepository {
    fun save(message: ChatMessage): ChatMessage
    /** 커서 페이지네이션: beforeId 보다 오래된 메시지 최신순 */
    fun findTop50ByRoomIdAndIdLessThanOrderByIdDesc(roomId: Long, beforeId: Long): List<ChatMessage>
    fun findTop50ByRoomIdOrderByIdDesc(roomId: Long): List<ChatMessage>
    fun findTopByRoomIdOrderByIdDesc(roomId: Long): ChatMessage?
    fun findMessageById(id: Long): ChatMessage?
    fun countByRoomIdAndIdGreaterThan(roomId: Long, afterId: Long): Long
}

/**
 * 채팅 첨부파일. 실제 파일은 [ChatFileStorage] 에 storedName 으로 저장한다 (공개 경로가 아님).
 * roomId 를 함께 저장해 내려받을 때 방 참여자인지 바로 확인할 수 있게 한다.
 */
@Entity
@Table(name = "chat_message_files")
class ChatMessageFile(
    @Column(nullable = false)
    val messageId: Long,

    @Column(nullable = false)
    val roomId: Long,

    @Column(nullable = false)
    val originalName: String,

    @Column(nullable = false, unique = true)
    val storedName: String,

    @Column(nullable = false)
    val contentType: String,

    @Column(nullable = false)
    val sizeBytes: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    var id: Long? = null
        protected set

    /** 브라우저에서 바로 보여 줘도 안전한 이미지 (SVG 는 스크립트를 품을 수 있어 제외) */
    val isPreviewableImage: Boolean get() = contentType in PREVIEWABLE_IMAGES

    companion object {
        val PREVIEWABLE_IMAGES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")
    }
}

interface ChatMessageFileRepository {
    fun save(file: ChatMessageFile): ChatMessageFile
    fun findFileById(id: Long): ChatMessageFile?
    fun findAllByMessageIdIn(messageIds: Collection<Long>): List<ChatMessageFile>
}

/** 채팅 첨부파일 저장소 포트 (서버 디스크 또는 S3) */
interface ChatFileStorage {
    /** @return 저장한 이름 (chat_message_files.stored_name) */
    fun store(content: InputStream, size: Long): String

    /** 없으면 null */
    fun open(storedName: String, serveAs: com.webrtcmeetpro.domain.common.ServeAs): com.webrtcmeetpro.domain.common.StoredFile?

    fun delete(storedName: String)
}
