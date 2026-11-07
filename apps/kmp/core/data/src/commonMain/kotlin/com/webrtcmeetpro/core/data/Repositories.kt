package com.webrtcmeetpro.core.data

import com.webrtcmeetpro.core.model.MeetingNotes
import com.webrtcmeetpro.core.model.UpdateChannelRequest
import com.webrtcmeetpro.core.model.Recording
import com.webrtcmeetpro.core.network.UploadFile
import com.webrtcmeetpro.core.model.TeamRole
import com.webrtcmeetpro.core.model.TeamMember
import com.webrtcmeetpro.core.model.AppNotification
import com.webrtcmeetpro.core.model.ChatMessage
import com.webrtcmeetpro.core.model.ChatCategory
import com.webrtcmeetpro.core.model.ChatRoom
import com.webrtcmeetpro.core.model.Invitation
import com.webrtcmeetpro.core.model.Me
import com.webrtcmeetpro.core.model.Meeting
import com.webrtcmeetpro.core.model.ScheduleMeetingRequest
import com.webrtcmeetpro.core.model.SignUpRequest
import com.webrtcmeetpro.core.model.TeamDetail
import com.webrtcmeetpro.core.model.TeamSummary
import com.webrtcmeetpro.core.network.ApiConfig
import com.webrtcmeetpro.core.network.LimeetApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val api: LimeetApi,
    private val session: SessionStore,
    private val users: UserRepository,
) {
    val isLoggedIn: Flow<Boolean> = session.isLoggedIn

    suspend fun login(email: String, password: String) {
        session.save(api.login(email.trim(), password))
        api.resetAuth()
    }

    suspend fun signUp(request: SignUpRequest) {
        session.save(api.signUp(request))
        api.resetAuth()
    }

    suspend fun isEmailAvailable(email: String): Boolean = api.emailAvailable(email.trim())

    /** 서버 세션 폐기가 실패해도(오프라인 등) 기기에서는 로그아웃시킨다 */
    suspend fun logout() {
        val refresh = session.load()?.refreshToken
        if (refresh != null) runCatching { api.logout(refresh) }
        clearLocal()
    }

    /** 비밀번호 바꾸기·만들기. 서버가 준 새 토큰으로 이 기기는 로그인 상태를 유지한다 */
    suspend fun changePassword(currentPassword: String?, newPassword: String) {
        session.save(api.changePassword(currentPassword, newPassword))
        api.resetAuth()
        users.refresh()
    }

    suspend fun resendVerification() = api.resendVerification()

    /** 회원 탈퇴. 서버가 세션을 모두 끊으므로 기기에서도 바로 로그아웃시킨다 */
    suspend fun withdraw(password: String?) {
        api.withdraw(password)
        clearLocal()
    }

    private suspend fun clearLocal() {
        session.clear()
        api.resetAuth()
        users.clear()
    }
}

/** 내 정보는 여러 화면(채팅 방 이름, 회의 주최자 판단 등)이 쓰므로 한 번 받아 두고 공유 */
class UserRepository(private val api: LimeetApi) {
    private val _me = MutableStateFlow<Me?>(null)
    val me: StateFlow<Me?> = _me.asStateFlow()

    suspend fun refresh(): Me = api.me().also { _me.value = it }

    suspend fun me(): Me = _me.value ?: refresh()

    fun clear() {
        _me.value = null
    }
}

class TeamRepository(private val api: LimeetApi) {
    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** 팀을 나가거나 삭제하면 알린다 (팀 목록이 다시 불러오게) */
    val changed: SharedFlow<Unit> = _changed

    suspend fun teams(): List<TeamSummary> = api.teams()
    suspend fun detail(teamId: Long): TeamDetail = api.team(teamId)
    suspend fun create(name: String, description: String?): TeamSummary = api.createTeam(name.trim(), description?.trim()?.ifEmpty { null })
    suspend fun invite(teamId: Long, email: String): Invitation = api.inviteToTeam(teamId, email.trim())
    suspend fun pendingInvitations(teamId: Long): List<Invitation> = api.teamInvitations(teamId)
    suspend fun cancelInvitation(teamId: Long, invitationId: Long) = api.cancelInvitation(teamId, invitationId)
    suspend fun changeRole(teamId: Long, userId: Long, role: TeamRole): TeamMember = api.changeTeamRole(teamId, userId, role)
    suspend fun leave(teamId: Long) = api.leaveTeam(teamId).also { _changed.tryEmit(Unit) }
    suspend fun removeMember(teamId: Long, userId: Long) = api.removeTeamMember(teamId, userId)
    suspend fun delete(teamId: Long) = api.deleteTeam(teamId).also { _changed.tryEmit(Unit) }
    suspend fun myInvitations(): List<Invitation> = api.myInvitations()
    suspend fun accept(invitationId: Long) = api.acceptInvitation(invitationId)
    suspend fun decline(invitationId: Long) = api.declineInvitation(invitationId)
}

class ChatRepository(private val api: LimeetApi) {
    suspend fun rooms(teamId: Long): List<ChatRoom> = api.chatRooms(teamId)
    suspend fun categories(teamId: Long): List<ChatCategory> = api.chatCategories(teamId)
    suspend fun room(roomId: Long): ChatRoom = api.chatRoom(roomId)
    suspend fun openDirect(teamId: Long, userId: Long): ChatRoom = api.openDirect(teamId, userId)
    suspend fun createGroup(teamId: Long, name: String, memberIds: List<Long>, categoryId: Long? = null): ChatRoom =
        api.createGroup(teamId, name.trim(), memberIds, categoryId)
    suspend fun updateChannel(roomId: Long, name: String, categoryId: Long?): ChatRoom =
        api.updateChannel(roomId, UpdateChannelRequest(name.trim(), categoryId, moveCategory = true))
    suspend fun createCategory(teamId: Long, name: String): ChatCategory = api.createCategory(teamId, name.trim())
    suspend fun renameCategory(categoryId: Long, name: String): ChatCategory = api.renameCategory(categoryId, name.trim())
    suspend fun deleteCategory(categoryId: Long) = api.deleteCategory(categoryId)
    suspend fun invite(roomId: Long, userIds: List<Long>) = api.inviteToRoom(roomId, userIds)
    suspend fun leave(roomId: Long) = api.leaveRoom(roomId)
    suspend fun messages(roomId: Long, before: Long? = null): List<ChatMessage> = api.messages(roomId, before)
    suspend fun send(roomId: Long, content: String): ChatMessage = api.sendMessage(roomId, content.trim())
    suspend fun sendFiles(roomId: Long, files: List<UploadFile>, content: String?): ChatMessage =
        api.sendFiles(roomId, files, content?.trim()?.ifEmpty { null })
    suspend fun markRead(roomId: Long, messageId: Long) = api.markRead(roomId, messageId)
    suspend fun edit(messageId: Long, content: String): ChatMessage = api.editMessage(messageId, content.trim())
    suspend fun delete(messageId: Long): ChatMessage = api.deleteMessage(messageId)
}

class MeetingRepository(
    private val api: LimeetApi,
    private val config: ApiConfig,
) {
    suspend fun upcoming(): List<Meeting> = api.meetings(past = false)
    suspend fun past(): List<Meeting> = api.meetings(past = true)
    suspend fun detail(meetingId: Long): Meeting = api.meeting(meetingId)
    suspend fun schedule(teamId: Long, request: ScheduleMeetingRequest): Meeting = api.scheduleMeeting(teamId, request)
    suspend fun cancel(meetingId: Long) = api.cancelMeeting(meetingId)
    suspend fun update(meetingId: Long, request: ScheduleMeetingRequest): Meeting = api.updateMeeting(meetingId, request)
    suspend fun recordings(meetingId: Long): List<Recording> = api.recordings(meetingId)
    suspend fun notes(meetingId: Long): MeetingNotes = api.meetingNotes(meetingId)
    suspend fun regenerateNotes(meetingId: Long): MeetingNotes = api.regenerateNotes(meetingId)

    /**
     * 화상회의는 아직 웹(mediasoup-client)에서만 된다. 로그인 상태를 1회용 코드로 넘겨
     * 브라우저에서 바로 회의실이 열리는 주소를 만든다.
     */
    suspend fun browserJoinUrl(meetingId: Long): String {
        val code = api.webHandoff().code
        return "${config.webUrl.trimEnd('/')}/oauth/callback?code=$code&next=/meet/$meetingId"
    }

    /** 채널 음성방: 진행 중인 음성방(없으면 새로)에 카메라를 끈 채로 들어가는 주소. 나오면 그 채널 화면으로 */
    suspend fun voiceJoinUrl(roomId: Long): String {
        val meeting = api.joinVoice(roomId)
        val code = api.webHandoff().code
        // next 안의 ? & 는 한 번 인코딩해야 한다 (/meet/12?voice=1&back=...)
        val next = "/meet/${meeting.id}?voice=1&back=/app/chat/${meeting.teamId}/$roomId"
            .replace("%", "%25").replace("?", "%3F").replace("&", "%26").replace("=", "%3D").replace("/", "%2F")
        return "${config.webUrl.trimEnd('/')}/oauth/callback?code=$code&next=$next"
    }
}

class NotificationRepository(private val api: LimeetApi) {
    suspend fun recent(): List<AppNotification> = api.notifications()
    suspend fun markRead(id: Long) = api.markNotificationRead(id)
    suspend fun markAllRead() = api.markAllNotificationsRead()
}

/** 탭 배지 (안 읽은 채팅 / 알림 수). 실시간 이벤트가 오거나 읽음 처리 후 refresh() */
class BadgeRepository(private val api: LimeetApi) {
    private val _chatUnread = MutableStateFlow(0L)
    val chatUnread: StateFlow<Long> = _chatUnread.asStateFlow()

    private val _notificationUnread = MutableStateFlow(0L)
    val notificationUnread: StateFlow<Long> = _notificationUnread.asStateFlow()

    suspend fun refresh() {
        runCatching { api.chatUnreadCount() }.onSuccess { _chatUnread.value = it }
        runCatching { api.notificationUnreadCount() }.onSuccess { _notificationUnread.value = it }
    }

    fun clear() {
        _chatUnread.value = 0
        _notificationUnread.value = 0
    }
}
