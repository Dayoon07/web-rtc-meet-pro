package com.webrtcmeetpro.application.chat

import com.webrtcmeetpro.domain.chat.ChatMessage
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
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

data class ChatUserView(val id: Long, val nickname: String, val profileImageUrl: String?)

data class ChatMessageView(
    val id: Long,
    val roomId: Long,
    val sender: ChatUserView?,
    val type: MessageType,
    val content: String,
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

@Service
@Transactional(readOnly = true)
class ChatService(
    private val roomRepository: ChatRoomRepository,
    private val memberRepository: ChatRoomMemberRepository,
    private val messageRepository: ChatMessageRepository,
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
        return roomRepository.findAllByIdInAndDeletedAtIsNull(mine.keys)
            .filter { it.teamId == teamId }
            .map { view(it, mine.getValue(it.id!!)) }
            .sortedByDescending { it.lastMessageAt ?: it.createdAt }
    }

    fun totalUnread(userId: Long): Long {
        val mine = memberRepository.findAllByUserIdAndLeftAtIsNull(userId)
        val live = roomRepository.findAllByIdInAndDeletedAtIsNull(mine.map { it.roomId }).map { it.id!! }.toSet()
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
        val groups = roomRepository.countByTeamIdAndRoomTypeAndDeletedAtIsNull(teamId, ChatRoomType.GROUP)
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
        return page.reversed().map { messageView(it, users) }
    }

    @Transactional
    fun send(roomId: Long, userId: Long, rawContent: String): ChatMessageView {
        val (room, me) = requireRoomMember(roomId, userId)
        val content = rawContent.trim()
        if (content.isEmpty()) throw BusinessException(ErrorCode.EMPTY_MESSAGE)
        if (content.length > MAX_LENGTH) throw BusinessException(ErrorCode.INVALID_INPUT)

        val saved = messageRepository.save(ChatMessage(roomId, userId, content))
        room.touch(saved.createdAt)
        me.markRead(saved.id!!)
        val view = messageView(saved, usersById(listOf(userId)))
        val members = memberRepository.findAllByRoomIdAndLeftAtIsNull(roomId).map { it.userId }
        realtime.push(members, RealtimePublisher.CHAT, ChatEvent("MESSAGE", room.teamId, roomId, view))
        return view
    }

    @Transactional
    fun markRead(roomId: Long, userId: Long, messageId: Long) {
        requireRoomMember(roomId, userId).second.markRead(messageId)
    }

    // ---------------------------------------------------------------

    /** 방 참여자 + 아직 그 팀의 팀원인지 함께 확인 (팀에서 나간 사람은 방도 못 본다) */
    private fun requireRoomMember(roomId: Long, userId: Long): Pair<ChatRoom, ChatRoomMember> {
        val room = roomRepository.findRoomById(roomId)?.takeIf { it.deletedAt == null }
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
        room.touch(saved.createdAt)
        return messageView(saved, emptyMap())
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

    private fun messageView(m: ChatMessage, users: Map<Long, User>) = ChatMessageView(
        id = m.id!!,
        roomId = m.roomId,
        sender = m.senderId?.let { users[it] }?.let(::userView),
        type = m.messageType,
        content = m.content,
        createdAt = m.createdAt,
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
            lastMessage = last?.let { messageView(it, users) },
            unreadCount = messageRepository.countByRoomIdAndIdGreaterThan(room.id!!, me.lastReadMessageId ?: 0),
            lastMessageAt = room.lastMessageAt,
            createdAt = room.createdAt,
        )
    }

    companion object {
        const val MAX_LENGTH = 4000
    }
}
