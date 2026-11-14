package com.webrtcmeetpro.domain.chat

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import org.hibernate.type.YesNoConverter
import java.io.InputStream
import java.nio.file.Path
import java.time.LocalDateTime

enum class ChatRoomType { DIRECT, GROUP }

/**
 * DIRECT: 1:1 방. 같은 두 사람의 방은 directKey 로 하나만 만든다.
 * GROUP : 채널. OWNER·ADMIN 이 만들고 카테고리(부서)에 넣을 수 있다.
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

    var categoryId: Long? = null
        protected set

    @Column(nullable = false)
    var sortOrder: Int = 0
        protected set

    /** 방 위쪽에 고정되는 글 하나 */
    var noticeContent: String? = null
        protected set
    var noticeBy: Long? = null
        protected set
    var noticeAt: LocalDateTime? = null
        protected set

    val isGroup: Boolean get() = roomType == ChatRoomType.GROUP

    fun postNotice(content: String, by: Long) {
        noticeContent = content.trim().take(NOTICE_MAX)
        noticeBy = by
        noticeAt = LocalDateTime.now()
    }

    fun clearNotice() {
        noticeContent = null
        noticeBy = null
        noticeAt = null
    }

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

    /** 팀을 삭제해도 대화 기록은 남긴다 */
    fun delete() = markDeleted()

    companion object {
        const val NOTICE_MAX = 1000

        fun directKeyOf(a: Long, b: Long) = "${minOf(a, b)}_${maxOf(a, b)}"

        fun direct(teamId: Long, creatorId: Long, otherId: Long) =
            ChatRoom(teamId, ChatRoomType.DIRECT, null, directKeyOf(creatorId, otherId), creatorId)

        fun group(teamId: Long, creatorId: Long, name: String) =
            ChatRoom(teamId, ChatRoomType.GROUP, name.trim().take(100), null, creatorId)
    }
}

/** 카테고리 = 부서. 예) "개발 연구소" 안에 #백오피스, #프론트오피스 */
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
    /** 채널 음성방을 동시에 두 개 만들지 않게 행을 잠근다 */
    fun lockRoomById(id: Long): ChatRoom?
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

    /** 새 메시지 푸시만 끈다. @멘션은 보낸다 */
    @Convert(converter = YesNoConverter::class)
    @Column(name = "muted_yn", nullable = false)
    var muted: Boolean = false
        protected set

    val isActive: Boolean get() = leftAt == null

    fun mute(on: Boolean) {
        muted = on
    }

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

/** SYSTEM: 보낸 사람이 없고 content 는 화면이 번역할 키 + 파라미터 JSON */
enum class MessageType { TEXT, FILE, SYSTEM }

@Entity
@Table(name = "chat_messages")
class ChatMessage(
    @Column(nullable = false)
    val roomId: Long,

    val senderId: Long?,

    /** 한글 4000자는 VARCHAR2(4000바이트)에 들어가지 않아 CLOB 으로 둔다 */
    @Lob
    @Column(name = "content")
    var content: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val messageType: MessageType = MessageType.TEXT,

    val meetingId: Long? = null,

    val replyToId: Long? = null,

    /** 스레드 답글은 채널 본문에는 안 보이고 스레드 창에만 보인다 */
    val threadRootId: Long? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    var id: Long? = null
        protected set

    /** 쉼표로 이은 user_id */
    @Column(name = "mention_user_ids")
    var mentionUserIdsText: String? = null
        protected set

    @Convert(converter = YesNoConverter::class)
    @Column(name = "mention_all", nullable = false)
    var mentionAll: Boolean = false
        protected set

    val mentionUserIds: List<Long> get() = mentionUserIdsText?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()

    fun mention(userIds: Collection<Long>, all: Boolean) {
        mentionUserIdsText = userIds.distinct().take(MAX_MENTIONS).joinToString(",").ifEmpty { null }
        mentionAll = all
    }

    /** 지우지 않은 답글 수 */
    @Column(nullable = false)
    var replyCount: Int = 0
        protected set

    var lastReplyAt: LocalDateTime? = null
        protected set

    /** 시작 글 아래 얼굴 몇 개를 보여 주려고 최근 [THREAD_USERS]명만 쉼표로 이어 둔다 */
    @Column(name = "thread_user_ids")
    var threadUserIdsText: String? = null
        protected set

    val threadUserIds: List<Long> get() = threadUserIdsText?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()

    val isThreadReply: Boolean get() = threadRootId != null

    fun addThreadReply(userId: Long, at: LocalDateTime) {
        replyCount += 1
        lastReplyAt = at
        threadUserIdsText = (listOf(userId) + threadUserIds.filter { it != userId }).take(THREAD_USERS).joinToString(",")
    }

    /** 얼굴·마지막 시각은 그대로 둔다 */
    fun removeThreadReply() {
        if (replyCount > 0) replyCount -= 1
    }

    /** 공통 컬럼 edited_dt 는 삭제 표시에도 바뀌어서 "수정됨" 표시용으로 따로 둔다 */
    var contentEditedAt: LocalDateTime? = null
        protected set

    init {
        require(messageType == MessageType.FILE || content != null) { "$messageType 메시지는 content 가 필요합니다." }
    }

    /** 파일 메시지의 글과 시스템 메시지는 고치지 않는다 */
    fun editContent(newContent: String, at: LocalDateTime = LocalDateTime.now()) {
        check(messageType == MessageType.TEXT && !deleted) { "글 메시지만 고칠 수 있습니다." }
        content = newContent
        contentEditedAt = at
    }

    /** 답장·읽음 위치가 가리키고 있어 행은 남긴다 */
    fun delete() = markDeleted()

    companion object {
        const val MAX_MENTIONS = 100
        const val THREAD_USERS = 5
    }
}

/** 한 사람이 같은 이모지는 하나만 단다. 다시 누르면 행을 지우지 않고 켜고 끈다 */
@Entity
@Table(name = "chat_message_reactions")
class ChatMessageReaction(
    @Column(nullable = false)
    val messageId: Long,

    @Column(nullable = false)
    val roomId: Long,

    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val emoji: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reaction_id")
    var id: Long? = null
        protected set

    fun toggle() {
        deleted = !deleted
    }

    companion object {
        /** 국기·피부색·가족 같은 조합 이모지도 들어가게 넉넉히 */
        const val MAX_LENGTH = 32
    }
}

interface ChatMessageReactionRepository {
    fun save(reaction: ChatMessageReaction): ChatMessageReaction
    fun findReactionById(id: Long): ChatMessageReaction?
    fun findAllByMessageIdInAndDeletedFalse(messageIds: Collection<Long>): List<ChatMessageReaction>
}

interface ChatMessageRepository {
    fun save(message: ChatMessage): ChatMessage
    /** 커서 페이지네이션. 스레드 답글은 채널 본문에서 뺀다 */
    fun findTop50ByRoomIdAndThreadRootIdIsNullAndIdLessThanOrderByIdDesc(roomId: Long, beforeId: Long): List<ChatMessage>
    fun findTop50ByRoomIdAndThreadRootIdIsNullOrderByIdDesc(roomId: Long): List<ChatMessage>
    fun findTopByRoomIdAndThreadRootIdIsNullOrderByIdDesc(roomId: Long): ChatMessage?
    fun findMessageById(id: Long): ChatMessage?
    /** 스레드 답글은 채널 본문에서 읽음 처리가 되지 않으니 세지 않는다 */
    fun countByRoomIdAndThreadRootIdIsNullAndIdGreaterThan(roomId: Long, afterId: Long): Long
    fun findTop500ByThreadRootIdOrderByIdAsc(threadRootId: Long): List<ChatMessage>
    fun findAllByThreadRootIdAndDeletedFalse(threadRootId: Long): List<ChatMessage>
    fun findAllByIdIn(ids: Collection<Long>): List<ChatMessage>
}

/** 내려받을 때 방 참여자인지 바로 확인하려고 roomId 를 같이 저장한다. 실제 파일은 공개 경로가 아닌 [ChatFileStorage] 에 둔다 */
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

    /** SVG 는 스크립트를 품을 수 있어서 뺀다 */
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

interface ChatFileStorage {
    fun store(content: InputStream, size: Long): String

    fun open(storedName: String, serveAs: com.webrtcmeetpro.domain.common.ServeAs): com.webrtcmeetpro.domain.common.StoredFile?

    fun delete(storedName: String)
}
