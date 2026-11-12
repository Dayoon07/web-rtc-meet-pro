package com.webrtcmeetpro.core.network

import com.webrtcmeetpro.core.model.MeetingNotes
import com.webrtcmeetpro.core.model.UpdateChannelRequest
import com.webrtcmeetpro.core.model.CategoryRequest
import com.webrtcmeetpro.core.model.Recording
import io.ktor.client.request.put
import io.ktor.http.HttpHeaders
import io.ktor.http.Headers
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.forms.formData
import io.ktor.client.plugins.timeout
import com.webrtcmeetpro.core.model.AppNotification
import com.webrtcmeetpro.core.model.AuthTokens
import com.webrtcmeetpro.core.model.ChatMessage
import com.webrtcmeetpro.core.model.PresenceView
import com.webrtcmeetpro.core.model.MuteRequest
import com.webrtcmeetpro.core.model.NoticeRequest
import com.webrtcmeetpro.core.model.ReactionRequest
import com.webrtcmeetpro.core.model.ChangePasswordRequest
import com.webrtcmeetpro.core.model.ClientErrorRequest
import com.webrtcmeetpro.core.model.ChatRoom
import com.webrtcmeetpro.core.model.CountResponse
import com.webrtcmeetpro.core.model.CreateGroupRequest
import com.webrtcmeetpro.core.model.ChangeRoleRequest
import com.webrtcmeetpro.core.model.CreateTeamRequest
import com.webrtcmeetpro.core.model.EmailAvailability
import com.webrtcmeetpro.core.model.Invitation
import com.webrtcmeetpro.core.model.InviteMembersRequest
import com.webrtcmeetpro.core.model.InviteRequest
import com.webrtcmeetpro.core.model.LoginRequest
import com.webrtcmeetpro.core.model.MarkReadRequest
import com.webrtcmeetpro.core.model.Me
import com.webrtcmeetpro.core.model.Meeting
import com.webrtcmeetpro.core.model.OpenDirectRequest
import com.webrtcmeetpro.core.model.RefreshRequest
import com.webrtcmeetpro.core.model.ScheduleMeetingRequest
import com.webrtcmeetpro.core.model.SendMessageRequest
import com.webrtcmeetpro.core.model.SignUpRequest
import com.webrtcmeetpro.core.model.ExpiryNotice
import com.webrtcmeetpro.core.model.ChatCategory
import com.webrtcmeetpro.core.model.TeamDetail
import com.webrtcmeetpro.core.model.TeamMember
import com.webrtcmeetpro.core.model.TeamRole
import com.webrtcmeetpro.core.model.TeamSummary
import com.webrtcmeetpro.core.model.WebHandoff
import com.webrtcmeetpro.core.model.WithdrawRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * 서버 REST API (apps/server 의 presentation 컨트롤러와 1:1).
 * 얇은 호출만 있어서 한 클래스에 두고, 도메인별 묶음은 core:data 의 리포지토리가 맡는다.
 */
class LimeetApi(private val client: HttpClient) {

    // auth
    suspend fun login(email: String, password: String): AuthTokens =
        client.post("api/auth/login") { setBody(LoginRequest(email, password)) }.body()

    suspend fun signUp(request: SignUpRequest): AuthTokens =
        client.post("api/auth/signup") { setBody(request) }.body()

    suspend fun emailAvailable(email: String): Boolean =
        client.get("api/auth/email-availability") { parameter("email", email) }.body<EmailAvailability>().available

    suspend fun logout(refreshToken: String) {
        client.post("api/auth/logout") { setBody(RefreshRequest(refreshToken)) }
    }

    /** 앱 → 브라우저로 로그인 상태를 넘기는 1회용 코드 (화상회의 입장용) */
    suspend fun webHandoff(): WebHandoff = client.post("api/sessions/web-handoff").body()

    // user / plan
    suspend fun me(): Me = client.get("api/users/me").body()

    /** 비밀번호 바꾸기·만들기. 다른 기기는 로그아웃되고 이 기기용 새 토큰이 온다 */
    suspend fun changePassword(currentPassword: String?, newPassword: String): AuthTokens =
        client.post("api/users/me/password") { setBody(ChangePasswordRequest(currentPassword, newPassword)) }.body()

    /** 가입 인증 메일 다시 보내기 (60초에 한 번) */
    suspend fun resendVerification() {
        client.post("api/users/me/email-verification")
    }

    suspend fun withdraw(password: String?) {
        client.post("api/users/me/withdraw") { setBody(WithdrawRequest(password)) }
    }

    /** 내가 결제한 워크스페이스 플랜 중 확인하지 않은 만료 (결제·구독 관리는 웹에서) */
    suspend fun expiryNotices(): List<ExpiryNotice> = client.get("api/subscriptions/notices").body()

    suspend fun chatCategories(teamId: Long): List<ChatCategory> = client.get("api/teams/$teamId/chat/categories").body()

    /** "플랜이 만료됐어요" 창을 닫음 */
    suspend fun acknowledgeExpiry(subscriptionId: Long) {
        client.post("api/subscriptions/$subscriptionId/acknowledge-expiry")
    }

    // team
    suspend fun teams(): List<TeamSummary> = client.get("api/teams").body()

    suspend fun team(teamId: Long): TeamDetail = client.get("api/teams/$teamId").body()

    suspend fun createTeam(name: String, description: String?): TeamSummary =
        client.post("api/teams") { setBody(CreateTeamRequest(name, description)) }.body()

    suspend fun inviteToTeam(teamId: Long, email: String): Invitation =
        client.post("api/teams/$teamId/invitations") { setBody(InviteRequest(email)) }.body()

    suspend fun teamInvitations(teamId: Long): List<Invitation> = client.get("api/teams/$teamId/invitations").body()

    suspend fun cancelInvitation(teamId: Long, invitationId: Long) {
        client.delete("api/teams/$teamId/invitations/$invitationId")
    }

    suspend fun changeTeamRole(teamId: Long, userId: Long, role: TeamRole): TeamMember =
        client.patch("api/teams/$teamId/members/$userId/role") { setBody(ChangeRoleRequest(role)) }.body()

    suspend fun leaveTeam(teamId: Long) {
        client.post("api/teams/$teamId/leave")
    }

    suspend fun removeTeamMember(teamId: Long, userId: Long) {
        client.delete("api/teams/$teamId/members/$userId")
    }

    suspend fun deleteTeam(teamId: Long) {
        client.delete("api/teams/$teamId")
    }

    suspend fun myInvitations(): List<Invitation> = client.get("api/invitations").body()

    suspend fun acceptInvitation(invitationId: Long) {
        client.post("api/invitations/$invitationId/accept")
    }

    suspend fun declineInvitation(invitationId: Long) {
        client.post("api/invitations/$invitationId/decline")
    }

    // chat
    suspend fun chatUnreadCount(): Long = client.get("api/chat/unread-count").body<CountResponse>().count

    suspend fun chatRooms(teamId: Long): List<ChatRoom> = client.get("api/teams/$teamId/chat/rooms").body()

    suspend fun chatRoom(roomId: Long): ChatRoom = client.get("api/chat/rooms/$roomId").body()

    suspend fun openDirect(teamId: Long, userId: Long): ChatRoom =
        client.post("api/teams/$teamId/chat/rooms/direct") { setBody(OpenDirectRequest(userId)) }.body()

    suspend fun createGroup(teamId: Long, name: String, memberIds: List<Long>, categoryId: Long? = null): ChatRoom =
        client.post("api/teams/$teamId/chat/rooms/group") { setBody(CreateGroupRequest(name, memberIds, categoryId)) }.body()

    /** 채널 상시 음성방 들어가기: 진행 중인 음성방, 없으면 새로 연다 */
    suspend fun joinVoice(roomId: Long): Meeting = client.post("api/chat/rooms/$roomId/voice").body()

    /** 채널 이름 바꾸기 · 카테고리 옮기기 (OWNER·ADMIN) */
    suspend fun updateChannel(roomId: Long, request: UpdateChannelRequest): ChatRoom =
        client.patch("api/chat/rooms/$roomId") { setBody(request) }.body()

    suspend fun createCategory(teamId: Long, name: String): ChatCategory =
        client.post("api/teams/$teamId/chat/categories") { setBody(CategoryRequest(name)) }.body()

    suspend fun renameCategory(categoryId: Long, name: String): ChatCategory =
        client.patch("api/chat/categories/$categoryId") { setBody(CategoryRequest(name)) }.body()

    /** 카테고리 지우기 (안의 채널은 카테고리 밖으로) */
    suspend fun deleteCategory(categoryId: Long) {
        client.delete("api/chat/categories/$categoryId")
    }

    suspend fun inviteToRoom(roomId: Long, userIds: List<Long>) {
        client.post("api/chat/rooms/$roomId/members") { setBody(InviteMembersRequest(userIds)) }
    }

    suspend fun leaveRoom(roomId: Long) {
        client.post("api/chat/rooms/$roomId/leave")
    }

    /** 내가 보낸 글 메시지 고치기 */
    suspend fun editMessage(messageId: Long, content: String): ChatMessage =
        client.patch("api/chat/messages/$messageId") { setBody(SendMessageRequest(content)) }.body()

    /** 메시지 지우기 (보낸 사람, 또는 팀 OWNER·ADMIN) */
    suspend fun deleteMessage(messageId: Long): ChatMessage = client.delete("api/chat/messages/$messageId").body()

    /** 최신 50개, before 를 주면 그 이전 50개. 오래된 것 → 최신 순 */
    suspend fun messages(roomId: Long, before: Long? = null): List<ChatMessage> =
        client.get("api/chat/rooms/$roomId/messages") { before?.let { parameter("before", it) } }.body()

    suspend fun sendMessage(roomId: Long, request: SendMessageRequest): ChatMessage =
        client.post("api/chat/rooms/$roomId/messages") { setBody(request) }.body()

    /** 이모지 반응 달기·떼기 (다시 누르면 떼어진다) */
    suspend fun react(messageId: Long, emoji: String): ChatMessage =
        client.post("api/chat/messages/$messageId/reactions") { setBody(ReactionRequest(emoji)) }.body()

    /** 워크스페이스에서 내가 들어간 방들의 메시지 검색 (글·첨부파일 이름, 최신 50개) */
    suspend fun searchMessages(teamId: Long, query: String): List<ChatMessage> =
        client.get("api/teams/$teamId/chat/search") { parameter("q", query) }.body()

    /** 방 공지 올리기·고치기 (null 이면 내리기) */
    suspend fun setNotice(roomId: Long, content: String?): ChatRoom =
        client.put("api/chat/rooms/$roomId/notice") { setBody(NoticeRequest(content)) }.body()

    /** 이 방 알림 끄기·켜기 */
    suspend fun setMuted(roomId: Long, muted: Boolean): ChatRoom =
        client.put("api/chat/rooms/$roomId/mute") { setBody(MuteRequest(muted)) }.body()

    /** "입력 중..." 알리기 */
    suspend fun typing(roomId: Long) {
        client.post("api/chat/rooms/$roomId/typing")
    }

    /** 워크스페이스에서 접속 중·자리 비움인 사람 */
    suspend fun presence(teamId: Long): List<PresenceView> = client.get("api/teams/$teamId/presence").body()

    /**
     * 파일 보내기 (multipart): files 여러 개 + content(함께 보낼 글, 선택) = FILE 메시지 1개.
     * 한글 파일 이름은 filename* (RFC 5987, UTF-8) 로 보낸다. 큰 파일은 기본 30초 제한을 넘을 수 있어 늘린다
     */
    suspend fun sendFiles(roomId: Long, files: List<UploadFile>, content: String?): ChatMessage =
        client.submitFormWithBinaryData(
            url = "api/chat/rooms/$roomId/files",
            formData = formData {
                content?.let { append("content", it, Headers.build { append(HttpHeaders.ContentType, "text/plain; charset=UTF-8") }) }
                files.forEach { f ->
                    append(
                        "files",
                        f.bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, f.contentType ?: "application/octet-stream")
                            append(HttpHeaders.ContentDisposition, "filename=\"${asciiFileName(f.name)}\"; filename*=UTF-8''${rfc5987(f.name)}")
                        },
                    )
                }
            },
        ) {
            timeout {
                requestTimeoutMillis = 5 * 60_000L
                socketTimeoutMillis = 60_000L
            }
        }.body()

    suspend fun markRead(roomId: Long, messageId: Long) {
        client.post("api/chat/rooms/$roomId/read") { setBody(MarkReadRequest(messageId)) }
    }

    // meeting
    suspend fun meetings(past: Boolean = false): List<Meeting> =
        client.get("api/meetings") { parameter("scope", if (past) "past" else "upcoming") }.body()

    suspend fun meeting(meetingId: Long): Meeting = client.get("api/meetings/$meetingId").body()

    suspend fun scheduleMeeting(teamId: Long, request: ScheduleMeetingRequest): Meeting =
        client.post("api/teams/$teamId/meetings") { setBody(request) }.body()

    /** 회의 녹화 목록 (녹화는 웹에서 주최자가 한다. 앱은 보기만) */
    suspend fun recordings(meetingId: Long): List<Recording> = client.get("api/meetings/$meetingId/recordings").body()

    /** AI 회의록 (켜고 받아쓰기는 회의 화면(웹)에서. 앱은 결과를 본다) */
    suspend fun meetingNotes(meetingId: Long): MeetingNotes = client.get("api/meetings/$meetingId/notes").body()

    suspend fun regenerateNotes(meetingId: Long): MeetingNotes = client.post("api/meetings/$meetingId/notes/regenerate").body()

    /** 예약 회의 고치기 (scheduledAt 필수) */
    suspend fun updateMeeting(meetingId: Long, request: ScheduleMeetingRequest): Meeting =
        client.put("api/meetings/$meetingId") { setBody(request) }.body()

    suspend fun cancelMeeting(meetingId: Long) {
        client.post("api/meetings/$meetingId/cancel")
    }

    // notification
    suspend fun notifications(): List<AppNotification> = client.get("api/notifications").body()

    suspend fun notificationUnreadCount(): Long = client.get("api/notifications/unread-count").body<CountResponse>().count

    suspend fun markNotificationRead(id: Long) {
        client.post("api/notifications/$id/read")
    }

    suspend fun markAllNotificationsRead() {
        client.post("api/notifications/read-all")
    }

    /** 로그인·로그아웃 뒤 캐시된 토큰을 비운다 */
    /** 앱 오류 신고 (로그인 전에도 된다) */
    suspend fun reportClientError(request: ClientErrorRequest) {
        client.post("api/client-errors") { setBody(request) }
    }

    fun resetAuth() = client.resetAuthTokens()
}

/** 채팅 첨부파일 한 개 (멀티파트로 보낸다) */
class UploadFile(val name: String, val contentType: String?, val bytes: ByteArray)

/** filename="..." 용: ASCII 가 아닌 글자와 따옴표는 _ 로 (서버는 filename* 을 먼저 쓴다) */
private fun asciiFileName(name: String): String =
    name.map { c -> if (c.code in 0x20..0x7E && c != '"' && c != '\\') c else '_' }.joinToString("")

/** RFC 5987 값 인코딩: UTF-8 바이트를 %XX 로 (영문·숫자와 일부 기호만 그대로) */
private fun rfc5987(value: String): String = buildString {
    val keep = "!#$&+-.^_`|~"
    value.encodeToByteArray().forEach { byte ->
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in keep) append(ch)
        else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0x0F])
    }
}
