package com.webrtcmeetpro.application.chat

import com.webrtcmeetpro.domain.chat.ChatFileStorage
import com.webrtcmeetpro.domain.chat.ChatMessage
import com.webrtcmeetpro.domain.chat.ChatMessageFile
import com.webrtcmeetpro.domain.chat.ChatMessageFileRepository
import com.webrtcmeetpro.domain.chat.ChatMessageRepository
import com.webrtcmeetpro.domain.chat.ChatRoom
import com.webrtcmeetpro.domain.chat.ChatRoomMember
import com.webrtcmeetpro.domain.chat.ChatRoomMemberRepository
import com.webrtcmeetpro.domain.chat.ChatRoomRepository
import com.webrtcmeetpro.domain.chat.ChatRoomType
import com.webrtcmeetpro.domain.chat.MessageType
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.infrastructure.realtime.RealtimePublisher
import com.webrtcmeetpro.application.notification.NotificationService
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.application.plan.PlanService
import com.webrtcmeetpro.application.team.TeamAccess
import com.webrtcmeetpro.domain.user.User
import com.webrtcmeetpro.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.json.JsonMapper
import java.io.InputStream
import java.nio.file.Path
import java.time.LocalDateTime

data class ChatUserView(val id: Long, val nickname: String, val profileImageUrl: String?)

/** url 은 서명된 내려받기 주소 (12~24시간 유효). image = 화면에 바로 미리보기 가능한 이미지 */
data class ChatFileView(
    val id: Long,
    val name: String,
    val size: Long,
    val contentType: String,
    val image: Boolean,
    val url: String,
)

data class ChatMessageView(
    val id: Long,
    val roomId: Long,
    val sender: ChatUserView?,
    val type: MessageType,
    /** FILE 메시지는 글 없이 파일만 보내면 null */
    val content: String?,
    val files: List<ChatFileView>,
    val createdAt: LocalDateTime,
)

data class ChatRoomView(
    val id: Long,
    val teamId: Long,
    val type: ChatRoomType,
    /** GROUP 방 이름. DIRECT 는 null → 클라이언트가 상대방 닉네임을 표시 */
    val name: String?,
    val createdBy: Long,
    val members: List<ChatUserView>,
    val lastMessage: ChatMessageView?,
    val unreadCount: Long,
    val lastMessageAt: LocalDateTime?,
    val createdAt: LocalDateTime,
)

/** /user/queue/chat 으로 가는 이벤트. ROOM = 방 목록 갱신 필요(새 방, 초대, 퇴장) */
data class ChatEvent(val type: String, val teamId: Long, val roomId: Long, val message: ChatMessageView? = null)

/** 올린 파일 1개. presentation 의 MultipartFile 에 의존하지 않도록 열기 함수로 받는다 (여러 번 열 수 있어야 함) */
data class FileUpload(val name: String?, val contentType: String?, val size: Long, val open: () -> InputStream)

/** 내려받을 파일. inline = 브라우저에서 바로 보여 줘도 되는 이미지 */
data class ChatFileDownload(val name: String, val contentType: String, val size: Long, val path: Path, val inline: Boolean, val expiresAt: Long)

@Service
@Transactional(readOnly = true)
class ChatService(
    private val roomRepository: ChatRoomRepository,
    private val memberRepository: ChatRoomMemberRepository,
    private val messageRepository: ChatMessageRepository,
    private val fileRepository: ChatMessageFileRepository,
    private val fileStorage: ChatFileStorage,
    private val fileUrls: ChatFileUrlSigner,
    private val userRepository: UserRepository,
    private val teamAccess: TeamAccess,
    private val planService: PlanService,
    private val notifications: NotificationService,
    private val realtime: RealtimePublisher,
    private val json: JsonMapper,
) {

    fun rooms(teamId: Long, userId: Long): List<ChatRoomView> {
        teamAccess.requireMember(teamId, userId)
        val mine = memberRepository.findAllByUserIdAndLeftAtIsNull(userId).associateBy { it.roomId }
        return roomRepository.findAllByIdInAndDeletedFalse(mine.keys)
            .filter { it.teamId == teamId }
            .map { view(it, mine.getValue(it.id!!)) }
            .sortedByDescending { it.lastMessageAt ?: it.createdAt }
    }

    fun totalUnread(userId: Long): Long {
        val mine = memberRepository.findAllByUserIdAndLeftAtIsNull(userId)
        val live = roomRepository.findAllByIdInAndDeletedFalse(mine.map { it.roomId }).map { it.id!! }.toSet()
        return mine.filter { it.roomId in live }
            .sumOf { messageRepository.countByRoomIdAndIdGreaterThan(it.roomId, it.lastReadMessageId ?: 0) }
    }

    fun room(roomId: Long, userId: Long): ChatRoomView {
        val (room, me) = requireRoomMember(roomId, userId)
        return view(room, me)
    }

    /** 1:1 방 열기. 이미 있으면 그 방을 돌려준다 */
    @Transactional
    fun openDirect(teamId: Long, userId: Long, targetId: Long): ChatRoomView {
        if (userId == targetId) throw BusinessException(ErrorCode.CANNOT_DIRECT_SELF)
        teamAccess.requireMember(teamId, userId)
        teamAccess.requireMember(teamId, targetId)

        val room = roomRepository.findByTeamIdAndDirectKey(teamId, ChatRoom.directKeyOf(userId, targetId))
            ?: roomRepository.save(ChatRoom.direct(teamId, userId, targetId))
        listOf(userId, targetId).forEach { ensureMember(room.id!!, it, invitedBy = null) }
        realtime.push(listOf(userId, targetId), RealtimePublisher.CHAT, ChatEvent("ROOM", teamId, room.id!!))
        return view(room, memberRepository.findByRoomIdAndUserId(room.id!!, userId)!!)
    }

    /** 1:N 방 만들기 — 팀을 만든 사람(OWNER)만 */
    @Transactional
    fun createGroup(teamId: Long, userId: Long, name: String, memberIds: Collection<Long>): ChatRoomView {
        val team = teamAccess.requireTeam(teamId)
        teamAccess.requireMember(teamId, userId)
        if (!team.isOwnedBy(userId)) throw BusinessException(ErrorCode.NOT_TEAM_OWNER)
        if (name.isBlank()) throw BusinessException(ErrorCode.INVALID_INPUT)

        val plan = planService.currentPlanOf(team.ownerId)
        val groups = roomRepository.countByTeamIdAndRoomTypeAndDeletedFalse(teamId, ChatRoomType.GROUP)
        if (!plan.canAddGroupRoom(groups)) throw BusinessException(ErrorCode.PLAN_GROUP_ROOM_LIMIT, mapOf("max" to plan.maxGroupRoomsPerTeam))

        val invitees = validTeamMembers(teamId, memberIds) - userId
        val room = roomRepository.save(ChatRoom.group(teamId, userId, name))
        ensureMember(room.id!!, userId, invitedBy = null)
        invitees.forEach { ensureMember(room.id!!, it, invitedBy = userId) }

        val users = usersById(invitees + userId)
        val created = systemMessage(room, "created", users.getValue(userId).nickname, invitees.mapNotNull { users[it]?.nickname })
        // 내가 만든 방의 안내 메시지는 나에겐 이미 읽은 것
        val me = memberRepository.findByRoomIdAndUserId(room.id!!, userId)!!.apply { markRead(created.id) }
        notifyInvited(room, users.getValue(userId), invitees)
        realtime.push(invitees + userId, RealtimePublisher.CHAT, ChatEvent("ROOM", teamId, room.id!!))
        return view(room, me)
    }

    /** 그룹 방에 팀원 초대 — 방 참여자라면 누구나 */
    @Transactional
    fun invite(roomId: Long, userId: Long, userIds: Collection<Long>): ChatRoomView {
        val (room, me) = requireRoomMember(roomId, userId)
        if (!room.isGroup) throw BusinessException(ErrorCode.DIRECT_ROOM_INVITE_NOT_ALLOWED)
        val current = memberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId }.toSet()
        val invitees = validTeamMembers(room.teamId, userIds) - current
        if (invitees.isEmpty()) return view(room, me)

        invitees.forEach { ensureMember(roomId, it, invitedBy = userId) }
        val users = usersById(invitees + userId)
        val msg = systemMessage(room, "invited", users.getValue(userId).nickname, invitees.mapNotNull { users[it]?.nickname })
        me.markRead(msg.id)
        notifyInvited(room, users.getValue(userId), invitees)
        realtime.push(current + invitees, RealtimePublisher.CHAT, ChatEvent("ROOM", room.teamId, roomId, msg))
        return view(room, me)
    }

    @Transactional
    fun leave(roomId: Long, userId: Long) {
        val (room, me) = requireRoomMember(roomId, userId)
        if (!room.isGroup) throw BusinessException(ErrorCode.INVALID_INPUT)
        me.leave()
        val msg = systemMessage(room, "left", usersById(listOf(userId)).getValue(userId).nickname, emptyList())
        val remaining = memberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId }
        realtime.push(remaining + userId, RealtimePublisher.CHAT, ChatEvent("ROOM", room.teamId, roomId, msg))
    }

    fun messages(roomId: Long, userId: Long, beforeId: Long?): List<ChatMessageView> {
        requireRoomMember(roomId, userId)
        val page = if (beforeId == null) messageRepository.findTop50ByRoomIdOrderByIdDesc(roomId)
        else messageRepository.findTop50ByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId)
        val users = usersById(page.mapNotNull { it.senderId })
        val files = filesByMessage(page)
        return page.reversed().map { messageView(it, users, files[it.id!!].orEmpty()) }
    }

    @Transactional
    fun send(roomId: Long, userId: Long, rawContent: String): ChatMessageView {
        val (room, me) = requireRoomMember(roomId, userId)
        val content = rawContent.trim()
        if (content.isEmpty()) throw BusinessException(ErrorCode.EMPTY_MESSAGE)
        if (content.length > MAX_LENGTH) throw BusinessException(ErrorCode.INVALID_INPUT)

        val saved = messageRepository.save(ChatMessage(roomId, userId, content))
        return publish(room, me, saved, emptyList())
    }

    /**
     * 파일 보내기: 파일 여러 개 + (선택) 함께 보낼 글 = FILE 메시지 1개.
     * 디스크에 먼저 쓰고 DB 에 기록한다. 트랜잭션이 롤백되면 디스크에 쓴 파일도 지운다.
     */
    @Transactional
    fun sendFiles(roomId: Long, userId: Long, rawContent: String?, uploads: List<FileUpload>): ChatMessageView {
        val (room, me) = requireRoomMember(roomId, userId)
        if (uploads.isEmpty()) throw BusinessException(ErrorCode.EMPTY_MESSAGE)
        if (uploads.size > MAX_FILES) throw BusinessException(ErrorCode.TOO_MANY_FILES, mapOf("max" to MAX_FILES))
        if (uploads.sumOf { it.size } > MAX_TOTAL_MB * 1024L * 1024L) throw tooLarge()
        val content = rawContent?.trim()?.takeIf { it.isNotEmpty() }
        if (content != null && content.length > MAX_LENGTH) throw BusinessException(ErrorCode.INVALID_INPUT)

        val checked = uploads.map(::inspect)
        val stored = mutableListOf<String>()
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) stored.forEach(fileStorage::delete)
            }
        })
        checked.forEach { (upload, _, _) -> upload.open().use { stored += fileStorage.store(it) } }

        val saved = messageRepository.save(ChatMessage(roomId, userId, content, MessageType.FILE))
        val files = checked.mapIndexed { i, (upload, name, type) ->
            fileRepository.save(ChatMessageFile(saved.id!!, roomId, name, stored[i], type, upload.size))
        }
        return publish(room, me, saved, files)
    }

    /** 서명이 맞고 지워지지 않은 파일만 내려준다. 서명이 틀리거나 만료되면 없는 파일처럼 보이게 한다 */
    fun openFile(fileId: Long, exp: Long, sig: String): ChatFileDownload {
        if (!fileUrls.isValid(fileId, exp, sig)) throw BusinessException(ErrorCode.FILE_NOT_FOUND)
        val file = fileRepository.findFileById(fileId)?.takeIf { !it.deleted } ?: throw BusinessException(ErrorCode.FILE_NOT_FOUND)
        val path = fileStorage.open(file.storedName) ?: throw BusinessException(ErrorCode.FILE_NOT_FOUND)
        return ChatFileDownload(file.originalName, file.contentType, file.sizeBytes, path, file.isPreviewableImage, exp)
    }

    @Transactional
    fun markRead(roomId: Long, userId: Long, messageId: Long) {
        requireRoomMember(roomId, userId).second.markRead(messageId)
    }

    // ---------------------------------------------------------------

    private fun publish(room: ChatRoom, me: ChatRoomMember, saved: ChatMessage, files: List<ChatMessageFile>): ChatMessageView {
        room.touch(saved.createdDt)
        me.markRead(saved.id!!)
        val view = messageView(saved, usersById(listOfNotNull(saved.senderId)), files)
        val members = memberRepository.findAllByRoomIdAndLeftAtIsNull(room.id!!).map { it.userId }
        realtime.push(members, RealtimePublisher.CHAT, ChatEvent("MESSAGE", room.teamId, room.id!!, view))
        return view
    }

    /** 크기·확장자 확인, 파일 이름 정리, 형식 판별. 이미지라고 해도 실제 내용(매직 바이트)이 이미지일 때만 이미지로 저장 */
    private fun inspect(upload: FileUpload): Triple<FileUpload, String, String> {
        if (upload.size <= 0) throw BusinessException(ErrorCode.EMPTY_FILE)
        if (upload.size > MAX_FILE_BYTES) throw tooLarge()
        val name = cleanFileName(upload.name)
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in BLOCKED_EXTENSIONS) throw BusinessException(ErrorCode.BLOCKED_FILE_TYPE, mapOf("ext" to extension))
        val head = upload.open().use { it.readNBytes(16) }
        val type = sniffImageType(head) ?: upload.contentType?.lowercase()?.takeIf {
            CONTENT_TYPE.matches(it) && it !in ChatMessageFile.PREVIEWABLE_IMAGES
        } ?: "application/octet-stream"
        return Triple(upload, name, type)
    }

    private fun tooLarge() = BusinessException(ErrorCode.FILE_TOO_LARGE, mapOf("max" to MAX_FILE_MB, "total" to MAX_TOTAL_MB))

    /** 요청을 보낸 사람이 볼 권한이 있으니 서명된 주소를 붙여 준다 */
    private fun fileView(f: ChatMessageFile) =
        ChatFileView(f.id!!, f.originalName, f.sizeBytes, f.contentType, f.isPreviewableImage, fileUrls.url(f.id!!))

    private fun filesByMessage(messages: List<ChatMessage>): Map<Long, List<ChatMessageFile>> {
        val ids = messages.filter { it.messageType == MessageType.FILE }.map { it.id!! }
        if (ids.isEmpty()) return emptyMap()
        return fileRepository.findAllByMessageIdIn(ids).filter { !it.deleted }.sortedBy { it.id }.groupBy { it.messageId }
    }

    /** 방 참여자 + 아직 그 팀의 팀원인지 함께 확인 (팀에서 나간 사람은 방도 못 본다) */
    private fun requireRoomMember(roomId: Long, userId: Long): Pair<ChatRoom, ChatRoomMember> {
        val room = roomRepository.findRoomById(roomId)?.takeIf { !it.deleted }
            ?: throw BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND)
        teamAccess.requireMember(room.teamId, userId)
        val me = memberRepository.findByRoomIdAndUserId(roomId, userId)?.takeIf { it.isActive }
            ?: throw BusinessException(ErrorCode.NOT_ROOM_MEMBER)
        return room to me
    }

    private fun validTeamMembers(teamId: Long, userIds: Collection<Long>): Set<Long> {
        val team = teamAccess.activeMemberIds(teamId)
        val requested = userIds.toSet()
        if (!team.containsAll(requested)) throw BusinessException(ErrorCode.NOT_TEAM_MEMBER)
        return requested
    }

    private fun ensureMember(roomId: Long, userId: Long, invitedBy: Long?) {
        val existing = memberRepository.findByRoomIdAndUserId(roomId, userId)
        when {
            existing == null -> memberRepository.save(ChatRoomMember(roomId, userId, invitedBy))
            !existing.isActive -> existing.rejoin()
        }
    }

    private fun systemMessage(room: ChatRoom, key: String, actor: String, targets: List<String>): ChatMessageView {
        val content = json.writeValueAsString(mapOf("key" to key, "actor" to actor, "targets" to targets))
        val saved = messageRepository.save(ChatMessage(room.id!!, null, content, MessageType.SYSTEM))
        room.touch(saved.createdDt)
        return messageView(saved, emptyMap(), emptyList())
    }

    private fun notifyInvited(room: ChatRoom, inviter: User, invitees: Collection<Long>) {
        notifications.notify(
            invitees,
            NotificationType.CHAT_ROOM_INVITED,
            mapOf("roomName" to room.name, "inviterName" to inviter.nickname),
            "/app/chat/${room.teamId}/${room.id}",
        )
    }

    private fun usersById(ids: Collection<Long>): Map<Long, User> =
        if (ids.isEmpty()) emptyMap() else userRepository.findAllByIdIn(ids.toSet()).associateBy { it.id!! }

    private fun userView(u: User) = ChatUserView(u.id!!, u.nickname, u.profileImageUrl)

    private fun messageView(m: ChatMessage, users: Map<Long, User>, files: List<ChatMessageFile>) = ChatMessageView(
        id = m.id!!,
        roomId = m.roomId,
        sender = m.senderId?.let { users[it] }?.let(::userView),
        type = m.messageType,
        content = m.content,
        files = files.map(::fileView),
        createdAt = m.createdDt,
    )

    private fun view(room: ChatRoom, me: ChatRoomMember): ChatRoomView {
        val members = memberRepository.findAllByRoomIdAndLeftAtIsNull(room.id!!)
        val last = messageRepository.findTopByRoomIdOrderByIdDesc(room.id!!)
        val users = usersById(members.map { it.userId } + listOfNotNull(last?.senderId))
        return ChatRoomView(
            id = room.id!!,
            teamId = room.teamId,
            type = room.roomType,
            name = room.name,
            createdBy = room.createdBy,
            members = members.mapNotNull { users[it.userId] }.map(::userView),
            lastMessage = last?.let { messageView(it, users, filesByMessage(listOf(it))[it.id!!].orEmpty()) },
            unreadCount = messageRepository.countByRoomIdAndIdGreaterThan(room.id!!, me.lastReadMessageId ?: 0),
            lastMessageAt = room.lastMessageAt,
            createdAt = room.createdDt,
        )
    }

    companion object {
        const val MAX_LENGTH = 4000
        const val MAX_FILES = 10
        const val MAX_FILE_MB = 20
        const val MAX_FILE_BYTES = MAX_FILE_MB * 1024L * 1024L
        /** application.yml 의 multipart max-request-size(51MB) 와 맞춘다 */
        const val MAX_TOTAL_MB = 50

        /** 받는 사람 PC 에서 바로 실행될 수 있는 형식은 막는다 */
        val BLOCKED_EXTENSIONS = setOf(
            "exe", "msi", "bat", "cmd", "com", "scr", "pif", "cpl", "vbs", "vbe", "wsf", "wsh", "ps1", "hta", "lnk", "reg",
        )
        private val CONTENT_TYPE = Regex("^[a-z0-9][a-z0-9!#$&^_.+-]{0,63}/[a-z0-9][a-z0-9!#$&^_.+-]{0,63}$")
        private val UNSAFE_NAME_CHARS = Regex("[\\p{Cntrl}\\\\/:*?\"<>|]")

        /** 경로(C:\fakepath\a.png 등)와 제어 문자를 지우고 255자 안으로 (확장자는 남긴다) */
        fun cleanFileName(raw: String?): String {
            val base = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\')
            val cleaned = base.replace(UNSAFE_NAME_CHARS, "_").trim().trim('.').ifBlank { "file" }
            if (cleaned.length <= 255) return cleaned
            val ext = cleaned.substringAfterLast('.', "").take(20)
            return if (ext.isEmpty()) cleaned.take(255) else cleaned.take(255 - ext.length - 1) + "." + ext
        }

        /** 파일 시그니처(매직 바이트)로 미리보기 가능한 이미지인지 판별 */
        fun sniffImageType(b: ByteArray): String? {
            fun startsWith(vararg sig: Int) = b.size >= sig.size && sig.indices.all { (b[it].toInt() and 0xFF) == sig[it] }
            return when {
                startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
                startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
                startsWith(0x47, 0x49, 0x46, 0x38) -> "image/gif"
                b.size >= 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
                else -> null
            }
        }
    }
}
