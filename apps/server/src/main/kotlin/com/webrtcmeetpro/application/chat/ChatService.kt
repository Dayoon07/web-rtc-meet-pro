package com.webrtcmeetpro.application.chat

import com.webrtcmeetpro.domain.chat.dto.res.ChatCategoryResDto
import com.webrtcmeetpro.domain.chat.dto.res.ChatEventResDto
import com.webrtcmeetpro.domain.chat.dto.res.ChatFileResDto
import com.webrtcmeetpro.domain.chat.dto.res.ChatMessageResDto
import com.webrtcmeetpro.domain.chat.dto.res.ChatNoticeResDto
import com.webrtcmeetpro.domain.chat.dto.res.ChatRoomResDto
import com.webrtcmeetpro.domain.chat.dto.res.ChatUserResDto
import com.webrtcmeetpro.domain.chat.dto.res.ReactionResDto
import com.webrtcmeetpro.domain.chat.dto.res.ReadStateResDto
import com.webrtcmeetpro.domain.chat.dto.res.ReplyPreviewResDto
import com.webrtcmeetpro.domain.chat.dto.res.ThreadResDto
import com.webrtcmeetpro.domain.chat.dto.res.VoicePresenceResDto
import com.webrtcmeetpro.domain.chat.ChatFileStorage
import com.webrtcmeetpro.domain.chat.ChatMessage
import com.webrtcmeetpro.domain.chat.ChatMessageFile
import com.webrtcmeetpro.domain.chat.ChatMessageFileRepository
import com.webrtcmeetpro.domain.chat.ChatMessageReactionRepository
import com.webrtcmeetpro.domain.chat.ChatQueryRepository
import com.webrtcmeetpro.domain.chat.ChatMessageReaction
import com.webrtcmeetpro.domain.chat.ChatMessageRepository
import com.webrtcmeetpro.domain.chat.ChatCategory
import com.webrtcmeetpro.domain.meeting.MeetingParticipantRepository
import com.webrtcmeetpro.domain.meeting.MeetingRepository
import com.webrtcmeetpro.domain.meeting.MeetingStatus
import com.webrtcmeetpro.domain.chat.ChatCategoryRepository
import com.webrtcmeetpro.domain.chat.ChatRoom
import com.webrtcmeetpro.domain.chat.ChatRoomMember
import com.webrtcmeetpro.domain.chat.ChatRoomMemberRepository
import com.webrtcmeetpro.domain.chat.ChatRoomRepository
import com.webrtcmeetpro.domain.chat.ChatRoomType
import com.webrtcmeetpro.domain.chat.MessageType
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.domain.common.ServeAs
import com.webrtcmeetpro.domain.common.StoredFile
import com.webrtcmeetpro.infrastructure.realtime.RealtimePublisher
import com.webrtcmeetpro.application.notification.NotificationService
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.team.TeamPermission
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
import com.webrtcmeetpro.application.push.PushService
import com.webrtcmeetpro.application.push.PushTexts

/** 올린 파일 1개. presentation 의 MultipartFile 에 의존하지 않도록 열기 함수로 받는다 (여러 번 열 수 있어야 함) */
data class FileUpload(val name: String?, val contentType: String?, val size: Long, val open: () -> InputStream)

/** 내려받을 파일. inline = 브라우저에서 바로 보여 줘도 되는 이미지 */
data class ChatFileDownload(val name: String, val contentType: String, val size: Long, val file: StoredFile, val inline: Boolean, val expiresAt: Long)

@Service
@Transactional(readOnly = true)
class ChatService(
    private val roomRepository: ChatRoomRepository,
    private val memberRepository: ChatRoomMemberRepository,
    private val messageRepository: ChatMessageRepository,
    private val fileRepository: ChatMessageFileRepository,
    private val reactionRepository: ChatMessageReactionRepository,
    private val chatQueries: ChatQueryRepository,
    private val fileStorage: ChatFileStorage,
    private val fileUrls: ChatFileUrlSigner,
    private val userRepository: UserRepository,
    private val teamAccess: TeamAccess,
    private val planService: PlanService,
    private val categoryRepository: ChatCategoryRepository,
    private val meetingRepository: MeetingRepository,
    private val participantRepository: MeetingParticipantRepository,
    private val notifications: NotificationService,
    private val push: PushService,
    private val realtime: RealtimePublisher,
    private val json: JsonMapper,
) {

    fun rooms(teamId: Long, userId: Long): List<ChatRoomResDto> {
        teamAccess.requireMember(teamId, userId)
        val mine = memberRepository.findAllByUserIdAndLeftAtIsNull(userId).associateBy { it.roomId }
        val rooms = roomRepository.findAllByIdInAndDeletedFalse(mine.keys).filter { it.teamId == teamId }
        val voices = voicePresence(rooms.filter { it.isGroup }.map { it.id!! })
        return rooms
            .map { view(it, mine.getValue(it.id!!), voices[it.id!!]) }
            .sortedByDescending { it.lastMessageAt ?: it.createdAt }
    }

    /** 워크스페이스마다 안 읽은 메시지 수 (0 인 워크스페이스는 빠진다) */
    fun unreadByTeam(userId: Long): Map<Long, Long> {
        val mine = memberRepository.findAllByUserIdAndLeftAtIsNull(userId)
        val teamOf = roomRepository.findAllByIdInAndDeletedFalse(mine.map { it.roomId }).associate { it.id!! to it.teamId }
        val result = linkedMapOf<Long, Long>()
        mine.forEach { m ->
            val teamId = teamOf[m.roomId] ?: return@forEach
            val n = messageRepository.countByRoomIdAndThreadRootIdIsNullAndIdGreaterThan(m.roomId, m.lastReadMessageId ?: 0)
            if (n > 0) result.merge(teamId, n, Long::plus)
        }
        return result
    }

    fun room(roomId: Long, userId: Long): ChatRoomResDto {
        val (room, me) = requireRoomMember(roomId, userId)
        return view(room, me)
    }

    /** 1:1 방 열기. 이미 있으면 그 방을 돌려준다 */
    @Transactional
    fun openDirect(teamId: Long, userId: Long, targetId: Long): ChatRoomResDto {
        if (userId == targetId) throw BusinessException(ErrorCode.CANNOT_DIRECT_SELF)
        teamAccess.requireMember(teamId, userId)
        teamAccess.requireMember(teamId, targetId)

        val room = roomRepository.findByTeamIdAndDirectKey(teamId, ChatRoom.directKeyOf(userId, targetId))
            ?: roomRepository.save(ChatRoom.direct(teamId, userId, targetId))
        listOf(userId, targetId).forEach { ensureMember(room.id!!, it, invitedBy = null) }
        realtime.push(listOf(userId, targetId), RealtimePublisher.CHAT, ChatEventResDto("ROOM", teamId, room.id!!))
        return view(room, memberRepository.findByRoomIdAndUserId(room.id!!, userId)!!)
    }

    /** 채널(1:N 방) 만들기 — CREATE_GROUP_ROOM 권한 (OWNER·ADMIN). categoryId 가 있으면 그 카테고리(부서) 안에 */
    @Transactional
    fun createGroup(teamId: Long, userId: Long, name: String, memberIds: Collection<Long>, categoryId: Long? = null): ChatRoomResDto {
        val team = teamAccess.requireTeam(teamId)
        teamAccess.requirePermission(teamId, userId, TeamPermission.CREATE_GROUP_ROOM)
        if (name.isBlank()) throw BusinessException(ErrorCode.INVALID_INPUT)

        val plan = planService.planOfTeam(team.id!!)
        val groups = roomRepository.countByTeamIdAndRoomTypeAndDeletedFalse(teamId, ChatRoomType.GROUP)
        if (!plan.canAddGroupRoom(groups)) throw BusinessException(ErrorCode.PLAN_GROUP_ROOM_LIMIT, mapOf("max" to (plan.maxGroupRoomsPerTeam ?: 0)))

        categoryId?.let { requireCategory(teamId, it) }

        val invitees = validTeamMembers(teamId, memberIds) - userId
        val room = roomRepository.save(ChatRoom.group(teamId, userId, name).apply { moveTo(categoryId) })
        ensureMember(room.id!!, userId, invitedBy = null)
        invitees.forEach { ensureMember(room.id!!, it, invitedBy = userId) }

        val users = usersById(invitees + userId)
        val created = systemMessage(room, "created", users.getValue(userId).nickname, invitees.mapNotNull { users[it]?.nickname })
        // 내가 만든 방의 안내 메시지는 나에겐 이미 읽은 것
        val me = memberRepository.findByRoomIdAndUserId(room.id!!, userId)!!.apply { markRead(created.id) }
        notifyInvited(room, users.getValue(userId), invitees)
        realtime.push(invitees + userId, RealtimePublisher.CHAT, ChatEventResDto("ROOM", teamId, room.id!!))
        return view(room, me)
    }

    /** 그룹 방에 팀원 초대 — 방 참여자라면 누구나 */
    @Transactional
    fun invite(roomId: Long, userId: Long, userIds: Collection<Long>): ChatRoomResDto {
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
        realtime.push(current + invitees, RealtimePublisher.CHAT, ChatEventResDto("ROOM", room.teamId, roomId, msg))
        return view(room, me)
    }

    /** 채널 이름 바꾸기 · 다른 카테고리로 옮기기 (OWNER·ADMIN). categoryId = null 이면 카테고리 밖으로 */
    @Transactional
    fun updateChannel(roomId: Long, userId: Long, name: String?, categoryId: Long?, moveCategory: Boolean): ChatRoomResDto {
        val (room, me) = requireRoomMember(roomId, userId)
        if (!room.isGroup) throw BusinessException(ErrorCode.INVALID_INPUT)
        teamAccess.requirePermission(room.teamId, userId, TeamPermission.CREATE_GROUP_ROOM)
        name?.let {
            if (it.isBlank()) throw BusinessException(ErrorCode.INVALID_INPUT)
            room.rename(it)
        }
        if (moveCategory) {
            categoryId?.let { requireCategory(room.teamId, it) }
            room.moveTo(categoryId)
        }
        pushTeam(room.teamId, ChatEventResDto("ROOM", room.teamId, roomId))
        return view(room, me)
    }

    /**
     * 방 공지 올리기·고치기·내리기 (content 가 비면 내리기).
     * 채널은 워크스페이스 OWNER·ADMIN, 1:1 방은 두 사람 모두. 방에 "공지를 올렸어요" 안내가 남는다
     */
    @Transactional
    fun setNotice(roomId: Long, userId: Long, content: String?): ChatRoomResDto {
        val (room, me) = requireRoomMember(roomId, userId)
        if (room.isGroup) teamAccess.requirePermission(room.teamId, userId, TeamPermission.CREATE_GROUP_ROOM)
        val text = content?.trim().orEmpty()
        if (text.isEmpty() && room.noticeContent == null) return view(room, me)
        if (text.isEmpty()) room.clearNotice() else room.postNotice(text, userId)
        val actor = usersById(listOf(userId)).getValue(userId).nickname
        val msg = systemMessage(room, if (text.isEmpty()) "noticeCleared" else "noticeSet", actor, emptyList())
        me.markRead(msg.id)
        val members = memberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId }
        realtime.push(members, RealtimePublisher.CHAT, ChatEventResDto("ROOM", room.teamId, roomId, msg))
        return view(room, me)
    }

    // ----------------------------------------------------------------- 카테고리 (부서)

    fun categories(teamId: Long, userId: Long): List<ChatCategoryResDto> {
        teamAccess.requireMember(teamId, userId)
        return categoryRepository.findAllByTeamIdAndDeletedFalseOrderBySortOrderAscIdAsc(teamId).map(::categoryView)
    }

    @Transactional
    fun createCategory(teamId: Long, userId: Long, name: String): ChatCategoryResDto {
        teamAccess.requirePermission(teamId, userId, TeamPermission.CREATE_GROUP_ROOM)
        if (name.isBlank()) throw BusinessException(ErrorCode.INVALID_INPUT)
        val next = (categoryRepository.findAllByTeamIdAndDeletedFalseOrderBySortOrderAscIdAsc(teamId).maxOfOrNull { it.sortOrder } ?: -1) + 1
        val saved = categoryRepository.save(ChatCategory(teamId, name.trim().take(50), next))
        pushTeam(teamId, ChatEventResDto("CATEGORIES", teamId, 0))
        return categoryView(saved)
    }

    @Transactional
    fun renameCategory(categoryId: Long, userId: Long, name: String): ChatCategoryResDto {
        val category = categoryRepository.findCategoryById(categoryId)?.takeIf { !it.deleted } ?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)
        teamAccess.requirePermission(category.teamId, userId, TeamPermission.CREATE_GROUP_ROOM)
        if (name.isBlank()) throw BusinessException(ErrorCode.INVALID_INPUT)
        category.rename(name)
        pushTeam(category.teamId, ChatEventResDto("CATEGORIES", category.teamId, 0))
        return categoryView(category)
    }

    /** 카테고리 지우기. 안에 있던 채널은 지우지 않고 카테고리 밖으로 꺼낸다 */
    @Transactional
    fun deleteCategory(categoryId: Long, userId: Long) {
        val category = categoryRepository.findCategoryById(categoryId)?.takeIf { !it.deleted } ?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)
        teamAccess.requirePermission(category.teamId, userId, TeamPermission.CREATE_GROUP_ROOM)
        roomRepository.findAllByTeamIdAndDeletedFalse(category.teamId).filter { it.categoryId == categoryId }.forEach { it.moveTo(null) }
        category.delete()
        pushTeam(category.teamId, ChatEventResDto("CATEGORIES", category.teamId, 0))
    }

    private fun requireCategory(teamId: Long, categoryId: Long): ChatCategory =
        categoryRepository.findCategoryById(categoryId)?.takeIf { it.teamId == teamId && !it.deleted }
            ?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)

    private fun categoryView(c: ChatCategory) = ChatCategoryResDto(c.id!!, c.name, c.sortOrder)

    /** 워크스페이스 멤버 모두에게 (카테고리·채널 이름이 바뀌면 사이드바를 다시 그리게) */
    private fun pushTeam(teamId: Long, event: ChatEventResDto) {
        realtime.push(teamAccess.activeMemberIds(teamId), RealtimePublisher.CHAT, event)
    }

    @Transactional
    fun leave(roomId: Long, userId: Long) {
        val (room, me) = requireRoomMember(roomId, userId)
        if (!room.isGroup) throw BusinessException(ErrorCode.INVALID_INPUT)
        me.leave()
        val msg = systemMessage(room, "left", usersById(listOf(userId)).getValue(userId).nickname, emptyList())
        val remaining = memberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId }
        realtime.push(remaining + userId, RealtimePublisher.CHAT, ChatEventResDto("ROOM", room.teamId, roomId, msg))
    }

    fun messages(roomId: Long, userId: Long, beforeId: Long?): List<ChatMessageResDto> {
        requireRoomMember(roomId, userId)
        val page = if (beforeId == null) messageRepository.findTop50ByRoomIdAndThreadRootIdIsNullOrderByIdDesc(roomId)
        else messageRepository.findTop50ByRoomIdAndThreadRootIdIsNullAndIdLessThanOrderByIdDesc(roomId, beforeId)
        return views(page.reversed())
    }

    /** 워크스페이스에서 내가 들어가 있는 방들의 메시지 검색 (글·첨부파일 이름, 최신 50개) */
    fun search(teamId: Long, userId: Long, rawQuery: String): List<ChatMessageResDto> {
        teamAccess.requireMember(teamId, userId)
        val query = rawQuery.trim().lowercase()
        if (query.isEmpty() || query.length > 100) throw BusinessException(ErrorCode.INVALID_INPUT)
        val mine = memberRepository.findAllByUserIdAndLeftAtIsNull(userId).map { it.roomId }
        val roomIds = roomRepository.findAllByIdInAndDeletedFalse(mine).filter { it.teamId == teamId }.map { it.id!! }
        if (roomIds.isEmpty()) return emptyList()
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val ids = chatQueries.searchMessageIds(roomIds, "%$escaped%", SEARCH_LIMIT)
        if (ids.isEmpty()) return emptyList()
        return views(messageRepository.findAllByIdIn(ids).sortedByDescending { it.id })
    }

    /** 스레드 창: 시작 글과 답글 (messageId 가 답글이면 그 스레드) */
    fun thread(messageId: Long, userId: Long): ThreadResDto {
        val message = messageRepository.findMessageById(messageId) ?: throw BusinessException(ErrorCode.MESSAGE_NOT_FOUND)
        val root = message.threadRootId?.let { messageRepository.findMessageById(it) } ?: message
        requireRoomMember(root.roomId, userId)
        if (root.messageType == MessageType.SYSTEM) throw BusinessException(ErrorCode.MESSAGE_NOT_FOUND)
        val all = views(listOf(root) + messageRepository.findTop500ByThreadRootIdOrderByIdAsc(root.id!!))
        return ThreadResDto(all.first(), all.drop(1))
    }

    /**
     * 글 보내기. replyToId = 답장할 메시지(같은 방), mentionIds = @멘션한 방 사람, mentionAll = @모두,
     * threadRootId = 스레드에 답글 (채널 본문에는 안 보인다).
     * 멘션된 사람(나 빼고)에게 CHAT_MENTION, 스레드에 있던 사람에게 THREAD_REPLY 알림
     */
    @Transactional
    fun send(
        roomId: Long,
        userId: Long,
        rawContent: String,
        replyToId: Long? = null,
        mentionIds: Collection<Long> = emptyList(),
        mentionAll: Boolean = false,
        threadRootId: Long? = null,
    ): ChatMessageResDto {
        val (room, me) = requireRoomMember(roomId, userId)
        val content = rawContent.trim()
        if (content.isEmpty()) throw BusinessException(ErrorCode.EMPTY_MESSAGE)
        if (content.length > MAX_LENGTH) throw BusinessException(ErrorCode.INVALID_INPUT)
        if (replyToId != null) {
            val original = messageRepository.findMessageById(replyToId)
            if (original == null || original.roomId != roomId || original.messageType == MessageType.SYSTEM) throw BusinessException(ErrorCode.MESSAGE_NOT_FOUND)
        }
        val root = threadRootIn(roomId, threadRootId)
        val members = memberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId }.toSet()
        val mentioned = mentionIds.filter { it in members && it != userId }

        val message = ChatMessage(roomId, userId, content, replyToId = replyToId, threadRootId = root?.id)
        message.mention(mentioned, mentionAll)
        val saved = messageRepository.save(message)
        val view = publish(room, me, saved, emptyList(), root)

        val targets = if (mentionAll) members - userId else mentioned.toSet()
        if (targets.isNotEmpty()) {
            notifications.notify(
                targets,
                NotificationType.CHAT_MENTION,
                mapOf("roomName" to room.name, "senderName" to view.sender?.nickname, "preview" to content.take(80)),
                chatLink(room, root?.id),
            )
        }
        root?.let { notifyThread(room, it, saved, view, members, skip = targets) }
        return view
    }

    /** 스레드 시작 글 (같은 방, 시스템 메시지·지운 글 안 됨). 답글을 고르면 그 스레드의 시작 글 */
    private fun threadRootIn(roomId: Long, threadRootId: Long?): ChatMessage? {
        if (threadRootId == null) return null
        val picked = messageRepository.findMessageById(threadRootId)
        val root = picked?.threadRootId?.let { messageRepository.findMessageById(it) } ?: picked
        if (root == null || root.roomId != roomId || root.messageType == MessageType.SYSTEM || root.deleted) throw BusinessException(ErrorCode.MESSAGE_NOT_FOUND)
        return root
    }

    /**
     * 스레드 답글 알림: 시작 글을 쓴 사람 + 그 스레드에 답글을 단 사람 (보낸 사람·멘션 알림을 받은 사람·방을 나간 사람 빼고).
     * 방 알림을 꺼도 온다 (내가 끼어 있는 대화라서)
     */
    private fun notifyThread(room: ChatRoom, root: ChatMessage, saved: ChatMessage, view: ChatMessageResDto, members: Set<Long>, skip: Set<Long>) {
        val repliers = messageRepository.findAllByThreadRootIdAndDeletedFalse(root.id!!).mapNotNull { it.senderId }
        val participants = (listOfNotNull(root.senderId) + repliers).toSet()
        val targets = participants.filter { it in members && it != saved.senderId && it !in skip }
        if (targets.isEmpty()) return
        val preview = saved.content?.replace(Regex("\\s+"), " ")?.take(80)
        notifications.notify(
            targets,
            NotificationType.THREAD_REPLY,
            mapOf("roomName" to room.name, "senderName" to view.sender?.nickname, "preview" to preview, "rootId" to root.id),
            chatLink(room, root.id),
        )
    }

    private fun chatLink(room: ChatRoom, threadRootId: Long?) =
        "/app/chat/${room.teamId}/${room.id}" + (threadRootId?.let { "?thread=$it" } ?: "")

    /** "입력 중..." 알리기: 방의 다른 사람들에게만 (저장하지 않는다. 화면이 몇 초마다 다시 보낸다) */
    fun typing(roomId: Long, userId: Long) {
        val (room, _) = requireRoomMember(roomId, userId)
        val me = usersById(listOf(userId)).getValue(userId)
        val others = memberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId } - userId
        realtime.push(others, RealtimePublisher.CHAT, ChatEventResDto("TYPING", room.teamId, roomId, typing = userView(me)))
    }

    /** 이 방 알림 끄기·켜기 (나만) */
    @Transactional
    fun setMuted(roomId: Long, userId: Long, muted: Boolean): ChatRoomResDto {
        val (room, me) = requireRoomMember(roomId, userId)
        me.mute(muted)
        return view(room, me)
    }

    /** 이모지 반응 달기·떼기 (방 사람 누구나). 다시 누르면 떼어진다 */
    @Transactional
    fun react(messageId: Long, userId: Long, rawEmoji: String): ChatMessageResDto {
        val emoji = rawEmoji.trim()
        // 이모지만 받는다: 비었거나 길거나 영문·숫자가 섞이면 거절
        if (emoji.isEmpty() || emoji.length > ChatMessageReaction.MAX_LENGTH || emoji.any { it.code < 128 && it.isLetterOrDigit() }) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        val message = messageRepository.findMessageById(messageId)?.takeIf { !it.deleted && it.messageType != MessageType.SYSTEM }
            ?: throw BusinessException(ErrorCode.MESSAGE_NOT_FOUND)
        val (room, _) = requireRoomMember(message.roomId, userId)
        val existing = chatQueries.findReactionId(messageId, userId, emoji)?.let(reactionRepository::findReactionById)
        if (existing == null) reactionRepository.save(ChatMessageReaction(messageId, message.roomId, userId, emoji)) else existing.toggle()
        return publishUpdate(room, message)
    }

    /**
     * 파일 보내기: 파일 여러 개 + (선택) 함께 보낼 글 = FILE 메시지 1개.
     * 디스크에 먼저 쓰고 DB 에 기록한다. 트랜잭션이 롤백되면 디스크에 쓴 파일도 지운다.
     */
    @Transactional
    fun sendFiles(roomId: Long, userId: Long, rawContent: String?, uploads: List<FileUpload>, threadRootId: Long? = null): ChatMessageResDto {
        val (room, me) = requireRoomMember(roomId, userId)
        val root = threadRootIn(roomId, threadRootId)
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
        checked.forEach { (upload, _, _) -> upload.open().use { stored += fileStorage.store(it, upload.size) } }

        val saved = messageRepository.save(ChatMessage(roomId, userId, content, MessageType.FILE, threadRootId = root?.id))
        val files = checked.mapIndexed { i, (upload, name, type) ->
            fileRepository.save(ChatMessageFile(saved.id!!, roomId, name, stored[i], type, upload.size))
        }
        val view = publish(room, me, saved, files, root)
        if (root != null) {
            val members = memberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId }.toSet()
            notifyThread(room, root, saved, view, members, skip = emptySet())
        }
        return view
    }

    /** 서명이 맞고 지워지지 않은 파일만 내려준다. 서명이 틀리거나 만료되면 없는 파일처럼 보이게 한다 */
    fun openFile(fileId: Long, exp: Long, sig: String, download: Boolean = false): ChatFileDownload {
        if (!fileUrls.isValid(fileId, exp, sig)) throw BusinessException(ErrorCode.FILE_NOT_FOUND)
        val file = fileRepository.findFileById(fileId)?.takeIf { !it.deleted } ?: throw BusinessException(ErrorCode.FILE_NOT_FOUND)
        val inline = file.isPreviewableImage && !download
        val serveAs = ServeAs(file.originalName, if (inline) file.contentType else "application/octet-stream", attachment = !inline)
        val stored = fileStorage.open(file.storedName, serveAs) ?: throw BusinessException(ErrorCode.FILE_NOT_FOUND)
        return ChatFileDownload(file.originalName, serveAs.contentType, file.sizeBytes, stored, inline, exp)
    }

    /** 내가 보낸 글 메시지 고치기 */
    @Transactional
    fun edit(messageId: Long, userId: Long, rawContent: String): ChatMessageResDto {
        val message = messageRepository.findMessageById(messageId)?.takeIf { !it.deleted } ?: throw BusinessException(ErrorCode.MESSAGE_NOT_FOUND)
        val (room, _) = requireRoomMember(message.roomId, userId)
        if (message.senderId != userId) throw BusinessException(ErrorCode.NOT_MESSAGE_SENDER)
        if (message.messageType != MessageType.TEXT) throw BusinessException(ErrorCode.MESSAGE_NOT_EDITABLE)
        val content = rawContent.trim()
        if (content.isEmpty()) throw BusinessException(ErrorCode.EMPTY_MESSAGE)
        if (content.length > MAX_LENGTH) throw BusinessException(ErrorCode.INVALID_INPUT)
        if (content != message.content) message.editContent(content)
        return publishUpdate(room, message)
    }

    /** 메시지 지우기: 보낸 사람, 또는 팀 OWNER·ADMIN(DELETE_ANY_MESSAGE). 시스템 메시지는 못 지운다 */
    @Transactional
    fun delete(messageId: Long, userId: Long): ChatMessageResDto {
        val message = messageRepository.findMessageById(messageId) ?: throw BusinessException(ErrorCode.MESSAGE_NOT_FOUND)
        val (room, _) = requireRoomMember(message.roomId, userId)
        if (message.messageType == MessageType.SYSTEM) throw BusinessException(ErrorCode.CANNOT_DELETE_MESSAGE)
        if (message.senderId != userId) teamAccess.requirePermission(room.teamId, userId, TeamPermission.DELETE_ANY_MESSAGE)
        if (!message.deleted) {
            message.delete()
            // 스레드 답글이면 시작 글의 답글 수를 줄여 다시 보낸다
            message.threadRootId?.let(messageRepository::findMessageById)?.let { root ->
                root.removeThreadReply()
                publishUpdate(room, root)
            }
        }
        return publishUpdate(room, message)
    }

    /** AI 회의록이 준비되면 채널(음성방이 열린 그 채널)에 카드로 올린다. SYSTEM 메시지, key = meetingNotes */
    @Transactional
    fun postMeetingNotes(roomId: Long, meetingId: Long, params: Map<String, Any?>) {
        val room = roomRepository.findRoomById(roomId)?.takeIf { !it.deleted } ?: return
        val content = json.writeValueAsString(mapOf("key" to "meetingNotes", "meetingId" to meetingId) + params)
        val saved = messageRepository.save(ChatMessage(roomId, null, content, MessageType.SYSTEM, meetingId = meetingId))
        room.touch(saved.createdDt)
        val view = views(listOf(saved)).single()
        val members = memberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId }
        realtime.push(members, RealtimePublisher.CHAT, ChatEventResDto("MESSAGE", room.teamId, roomId, view))
    }

    @Transactional
    fun markRead(roomId: Long, userId: Long, messageId: Long) {
        val (room, me) = requireRoomMember(roomId, userId)
        val before = me.lastReadMessageId
        me.markRead(messageId)
        if (me.lastReadMessageId == before) return
        val members = memberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId }
        realtime.push(members, RealtimePublisher.CHAT, ChatEventResDto("READ", room.teamId, roomId, read = ReadStateResDto(userId, me.lastReadMessageId)))
    }

    // ---------------------------------------------------------------

    /**
     * 새 메시지를 방 사람들에게. 스레드 답글이면 채널 순서·읽음 위치는 그대로 두고,
     * 시작 글의 답글 수를 올려 MESSAGE_UPDATED 로 같이 보낸다 (새 메시지 푸시 대신 스레드 알림이 간다)
     */
    private fun publish(room: ChatRoom, me: ChatRoomMember, saved: ChatMessage, files: List<ChatMessageFile>, root: ChatMessage? = null): ChatMessageResDto {
        if (root == null) {
            room.touch(saved.createdDt)
            me.markRead(saved.id!!)
        } else {
            root.addThreadReply(saved.senderId!!, saved.createdDt)
        }
        val view = views(listOf(saved)).single()
        val roomMembers = memberRepository.findAllByRoomIdAndLeftAtIsNull(room.id!!)
        val members = roomMembers.map { it.userId }
        realtime.push(members, RealtimePublisher.CHAT, ChatEventResDto("MESSAGE", room.teamId, room.id!!, view))
        if (root == null) pushNewMessage(room, saved, view, roomMembers) else publishUpdate(room, root)
        return view
    }

    /**
     * 새 메시지 브라우저 푸시: 보낸 사람·알림 끈 사람은 빼고, 멘션된 사람도 뺀다 (멘션 알림이 따로 간다).
     * 제목 = 채널 이름(#) 또는 1:1 상대, 내용 = (채널이면 "보낸 사람: ") 글 또는 "파일을 보냈어요"
     */
    private fun pushNewMessage(room: ChatRoom, saved: ChatMessage, view: ChatMessageResDto, roomMembers: List<ChatRoomMember>) {
        val mentioned = if (saved.mentionAll) roomMembers.map { it.userId }.toSet() else saved.mentionUserIds.toSet()
        val recipients = roomMembers.filter { it.userId != saved.senderId && !it.muted && it.userId !in mentioned }.map { it.userId }
        if (recipients.isEmpty()) return
        val senderName = view.sender?.nickname.orEmpty()
        val title = if (room.isGroup) "# ${room.name}" else senderName
        val text = saved.content?.replace(Regex("\\s+"), " ")?.take(140)
        push.chatMessage(recipients, title, "/app/chat/${room.teamId}/${room.id}", "room-${room.id}") { lang ->
            val body = text ?: PushTexts.text(lang, "file")
            if (room.isGroup) "$senderName: $body" else body
        }
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
        ChatFileResDto(f.id!!, f.originalName, f.sizeBytes, f.contentType, f.isPreviewableImage, fileUrls.url(f.id!!))

    /** 고치거나 지운 메시지를 방 사람들에게 다시 보낸다 (화면이 같은 id 를 바꿔 끼운다) */
    private fun publishUpdate(room: ChatRoom, message: ChatMessage): ChatMessageResDto {
        val view = views(listOf(message)).single()
        val members = memberRepository.findAllByRoomIdAndLeftAtIsNull(room.id!!).map { it.userId }
        realtime.push(members, RealtimePublisher.CHAT, ChatEventResDto("MESSAGE_UPDATED", room.teamId, room.id!!, view))
        return view
    }

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

    private fun systemMessage(room: ChatRoom, key: String, actor: String, targets: List<String>): ChatMessageResDto {
        val content = json.writeValueAsString(mapOf("key" to key, "actor" to actor, "targets" to targets))
        val saved = messageRepository.save(ChatMessage(room.id!!, null, content, MessageType.SYSTEM))
        room.touch(saved.createdDt)
        return views(listOf(saved)).single()
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

    private fun userView(u: User) = ChatUserResDto(u.id!!, u.nickname, u.profileImageUrl)

    /** 메시지 여러 개를 화면 모양으로. 보낸 사람·첨부파일·이모지 반응·답장 원문을 한 번에 불러온다 */
    private fun views(messages: List<ChatMessage>): List<ChatMessageResDto> {
        if (messages.isEmpty()) return emptyList()
        val replyIds = messages.mapNotNull { it.replyToId }
        val replies = if (replyIds.isEmpty()) emptyMap() else messageRepository.findAllByIdIn(replyIds).associateBy { it.id!! }
        val users = usersById(messages.mapNotNull { it.senderId } + replies.values.mapNotNull { it.senderId } + messages.flatMap { it.threadUserIds })
        val files = filesByMessage(messages + replies.values)
        val reactions = reactionRepository.findAllByMessageIdInAndDeletedFalse(messages.map { it.id!! }).groupBy { it.messageId }
        return messages.map { m ->
            messageView(m, users, files[m.id!!].orEmpty()).copy(
                replyTo = m.replyToId?.let(replies::get)?.let { r ->
                    ReplyPreviewResDto(
                        id = r.id!!,
                        sender = r.senderId?.let(users::get)?.let(::userView),
                        content = if (r.deleted) null else r.content?.take(200),
                        type = r.messageType,
                        fileCount = if (r.deleted) 0 else files[r.id!!].orEmpty().size,
                        deleted = r.deleted,
                    )
                },
                reactions = if (m.deleted) emptyList() else reactions[m.id!!].orEmpty().sortedBy { it.id }.groupBy { it.emoji }
                    .map { (emoji, list) -> ReactionResDto(emoji, list.size, list.map { it.userId }) },
                mentions = if (m.deleted) emptyList() else m.mentionUserIds,
                mentionAll = !m.deleted && m.mentionAll,
                threadRootId = m.threadRootId,
                replyCount = m.replyCount,
                lastReplyAt = m.lastReplyAt,
                threadUsers = m.threadUserIds.mapNotNull(users::get).map(::userView),
            )
        }
    }

    private fun messageView(m: ChatMessage, users: Map<Long, User>, files: List<ChatMessageFile>) = ChatMessageResDto(
        id = m.id!!,
        roomId = m.roomId,
        sender = m.senderId?.let { users[it] }?.let(::userView),
        type = m.messageType,
        // 지운 메시지는 내용·파일을 내려보내지 않는다
        content = if (m.deleted) null else m.content,
        files = if (m.deleted) emptyList() else files.map(::fileView),
        createdAt = m.createdDt,
        edited = m.contentEditedAt != null,
        deleted = m.deleted,
    )

    /** 채널들의 진행 중인 음성방과 들어와 있는 사람 (한 번에 모아서) */
    private fun voicePresence(roomIds: List<Long>): Map<Long, VoicePresenceResDto> {
        if (roomIds.isEmpty()) return emptyMap()
        val live = meetingRepository.findAllByChatRoomIdInAndStatus(roomIds, MeetingStatus.LIVE)
        val inRoom = live.associateWith { m -> participantRepository.findAllByMeetingIdAndLeftAtIsNull(m.id!!).map { it.userId }.distinct() }
        val users = usersById(inRoom.values.flatten())
        return live.filter { inRoom.getValue(it).isNotEmpty() }.associate { m ->
            m.chatRoomId!! to VoicePresenceResDto(m.id!!, inRoom.getValue(m).mapNotNull { users[it] }.map(::userView))
        }
    }

    private fun view(room: ChatRoom, me: ChatRoomMember, voice: VoicePresenceResDto? = if (room.isGroup) voicePresence(listOf(room.id!!))[room.id!!] else null): ChatRoomResDto {
        val members = memberRepository.findAllByRoomIdAndLeftAtIsNull(room.id!!)
        val last = messageRepository.findTopByRoomIdAndThreadRootIdIsNullOrderByIdDesc(room.id!!)
        val users = usersById(members.map { it.userId } + listOfNotNull(last?.senderId, room.noticeBy))
        return ChatRoomResDto(
            id = room.id!!,
            teamId = room.teamId,
            type = room.roomType,
            categoryId = room.categoryId,
            voice = voice,
            name = room.name,
            createdBy = room.createdBy,
            members = members.mapNotNull { users[it.userId] }.map(::userView),
            lastMessage = last?.let { messageView(it, users, filesByMessage(listOf(it))[it.id!!].orEmpty()) },
            reads = members.map { ReadStateResDto(it.userId, it.lastReadMessageId) },
            muted = me.muted,
            unreadCount = messageRepository.countByRoomIdAndThreadRootIdIsNullAndIdGreaterThan(room.id!!, me.lastReadMessageId ?: 0),
            lastMessageAt = room.lastMessageAt,
            createdAt = room.createdDt,
            notice = room.noticeContent?.let { ChatNoticeResDto(it, room.noticeBy?.let(users::get)?.let(::userView), room.noticeAt) },
        )
    }

    companion object {
        const val MAX_LENGTH = 4000
        const val SEARCH_LIMIT = 50
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
