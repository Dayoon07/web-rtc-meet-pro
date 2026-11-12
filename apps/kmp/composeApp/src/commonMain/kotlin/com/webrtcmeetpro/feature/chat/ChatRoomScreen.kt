@file:OptIn(ExperimentalFoundationApi::class)

package com.webrtcmeetpro.feature.chat

import com.webrtcmeetpro.core.data.MeetingRepository
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import com.webrtcmeetpro.core.model.ChatUser
import com.webrtcmeetpro.core.model.ReadState
import com.webrtcmeetpro.resources.chat_emoji
import com.webrtcmeetpro.resources.chat_thread_replies
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Forum
import com.webrtcmeetpro.resources.chat_mention_all
import com.webrtcmeetpro.resources.chat_mute
import com.webrtcmeetpro.resources.chat_notice_title
import com.webrtcmeetpro.resources.chat_unmute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import com.webrtcmeetpro.platform.inAppMeetingSupported
import com.webrtcmeetpro.resources.chat_voice_join
import com.webrtcmeetpro.core.model.ChatCategory
import androidx.compose.material.icons.outlined.Tune
import com.webrtcmeetpro.resources.chat_channel_settings
import com.webrtcmeetpro.resources.common_save
import com.webrtcmeetpro.resources.chat_delete_others_confirm
import com.webrtcmeetpro.resources.chat_delete_confirm
import com.webrtcmeetpro.resources.chat_delete
import com.webrtcmeetpro.resources.chat_edit
import com.webrtcmeetpro.resources.chat_edited
import com.webrtcmeetpro.resources.chat_deleted_message
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.webrtcmeetpro.resources.chat_remove_file
import com.webrtcmeetpro.resources.chat_attach
import com.webrtcmeetpro.platform.rememberFilePicker
import com.webrtcmeetpro.platform.PickedFile
import com.webrtcmeetpro.platform.ChatFileLimits
import com.webrtcmeetpro.core.network.UploadFile
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.BadgeRepository
import com.webrtcmeetpro.core.data.ChatRepository
import com.webrtcmeetpro.core.data.RealtimeRepository
import com.webrtcmeetpro.core.data.TeamRepository
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.designsystem.component.Avatar
import com.webrtcmeetpro.core.model.ChatMessage
import com.webrtcmeetpro.core.model.ChatRoom
import com.webrtcmeetpro.core.model.ChatRoomType
import com.webrtcmeetpro.core.model.MessageType
import com.webrtcmeetpro.core.model.TeamMember
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.platform.formatChatTime
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.chat_input_hint
import com.webrtcmeetpro.resources.chat_invite
import com.webrtcmeetpro.resources.chat_invite_none
import com.webrtcmeetpro.resources.chat_leave
import com.webrtcmeetpro.resources.chat_leave_confirm
import com.webrtcmeetpro.resources.chat_members
import com.webrtcmeetpro.resources.chat_send
import com.webrtcmeetpro.resources.common_cancel
import com.webrtcmeetpro.ui.DetailScaffold
import com.webrtcmeetpro.ui.ErrorSnackbarEffect
import com.webrtcmeetpro.ui.ErrorState
import com.webrtcmeetpro.ui.resolveImageUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** 지금 열려 있는 채팅방·스레드. 그 방의 새 메시지, 그 스레드의 답글 알림은 시스템 알림을 띄우지 않는다 */
class ChatPresence {
    val activeRoomId = MutableStateFlow<Long?>(null)
    val activeThreadId = MutableStateFlow<Long?>(null)
}

data class ChatRoomUiState(
    val room: ChatRoom? = null,
    val myId: Long = 0,
    val loadError: String? = null,
    /** 오래된 것 → 최신 순 */
    val messages: List<ChatMessage> = emptyList(),
    val hasOlder: Boolean = true,
    val loadingOlder: Boolean = false,
    val input: String = "",
    /** 보내기 전 첨부파일 */
    val pendingFiles: List<PickedFile> = emptyList(),
    val sending: Boolean = false,
    val errorCode: String? = null,
    val inviteCandidates: List<TeamMember>? = null,
    val left: Boolean = false,
    /** 팀 OWNER·ADMIN: 다른 사람 메시지도 지울 수 있다 */
    val canModerate: Boolean = false,
    /** 길게 눌러서 고르고 있는 메시지 (고치기·지우기 메뉴) */
    val selected: ChatMessage? = null,
    /** 고치는 중인 메시지 */
    val editing: ChatMessage? = null,
    /** 채널 이름·카테고리를 고칠 수 있는지 (워크스페이스 OWNER·ADMIN) */
    val canManageChannel: Boolean = false,
    /** 음성방 들어갈 주소 (만들어지면 화면이 연다) */
    val voiceUrl: String? = null,
    val joiningVoice: Boolean = false,
    val categories: List<ChatCategory> = emptyList(),
    /** 답장할 메시지 (입력창 위에 표시) */
    val replyTo: ChatMessage? = null,
    /** 입력창에서 고른 @멘션 (id → 닉네임). 보낼 때 글에 "@닉네임" 이 남아 있는 사람만 */
    val mentionPicks: Map<Long, String> = emptyMap(),
    /** 지금 입력 중인 사람 */
    val typing: List<ChatUser> = emptyList(),
    /** 반응을 고르는 메시지 (이모지 창) */
    val reactingTo: ChatMessage? = null,
    /** 공지 창 (null = 닫힘, 값 = 처음 글) */
    val noticeDraft: String? = null,
) {
    /** 공지는 1:1 방이면 누구나, 채널은 채널을 관리할 수 있는 사람 */
    val canNotice: Boolean get() = room?.type == ChatRoomType.DIRECT || canManageChannel
}

class ChatRoomViewModel(
    private val roomId: Long,
    private val chat: ChatRepository,
    private val meetings: MeetingRepository,
    private val teams: TeamRepository,
    private val users: UserRepository,
    private val badges: BadgeRepository,
    realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatRoomUiState())
    val state: StateFlow<ChatRoomUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            realtime.chatEvents.collect { event ->
                if (event.roomId != roomId) return@collect
                val message = event.message
                val read = event.read
                val typingUser = event.typing
                if (event.type == "TYPING") {
                    if (typingUser != null && typingUser.id != _state.value.myId) showTyping(typingUser)
                } else if (event.type == "READ") {
                    if (read != null) _state.update { s -> s.copy(room = s.room?.let { r -> r.copy(reads = r.reads.filter { it.userId != read.userId } + read) }) }
                } else if (event.type == "CATEGORIES") {
                    // 채널 목록 화면만 다시 그린다
                } else if (event.type == "MESSAGE" && message != null) {
                    message.sender?.let { hideTyping(it.id) }
                    // 스레드 답글은 채팅방 본문이 아니라 스레드 화면에만 (시작 글의 답글 수는 MESSAGE_UPDATED 로 온다)
                    if (message.threadRootId == null) {
                        append(message)
                        markRead()
                    }
                } else if (event.type == "MESSAGE_UPDATED" && message != null) {
                    replace(message)
                } else {
                    // ROOM: 초대·퇴장 → 참여자 목록 갱신
                    runCatching { chat.room(roomId) }.onSuccess { room -> _state.update { it.copy(room = room) } }
                }
            }
        }
    }

    fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            runCatching { Triple(users.me().id, chat.room(roomId), chat.messages(roomId)) }
                .onSuccess { (myId, room, messages) ->
                    _state.update { it.copy(myId = myId, room = room, messages = messages, hasOlder = messages.size >= PAGE) }
                    markRead()
                    runCatching { teams.detail(room.teamId) }
                        .onSuccess { t ->
                            _state.update { it.copy(canModerate = "DELETE_ANY_MESSAGE" in t.team.myPermissions, canManageChannel = t.team.canCreateGroupRoom) }
                        }
                    if (room.type == ChatRoomType.GROUP) {
                        runCatching { chat.categories(room.teamId) }.onSuccess { list -> _state.update { it.copy(categories = list) } }
                    }
                }
                .onFailure { e -> _state.update { it.copy(loadError = e.errorCode) } }
        }
    }

    fun loadOlder() {
        val s = _state.value
        val oldest = s.messages.firstOrNull()?.id ?: return
        if (!s.hasOlder || s.loadingOlder) return
        _state.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            runCatching { chat.messages(roomId, before = oldest) }
                .onSuccess { older ->
                    _state.update { it.copy(messages = older + it.messages, hasOlder = older.size >= PAGE, loadingOlder = false) }
                }
                .onFailure { _state.update { it.copy(loadingOlder = false) } }
        }
    }

    private var lastTypingSent: TimeMark? = null
    private val typingTimers = mutableMapOf<Long, Job>()

    /** 글을 쓰면 3초에 한 번씩 "입력 중" 을 알린다 */
    fun setInput(value: String) {
        _state.update { it.copy(input = value.take(4000)) }
        if (value.isBlank()) return
        if (lastTypingSent?.let { it.elapsedNow() < 3.seconds } == true) return
        lastTypingSent = TimeSource.Monotonic.markNow()
        viewModelScope.launch { runCatching { chat.typing(roomId) } }
    }

    /** 입력 중 표시는 5초 동안 새 신호가 없으면 사라진다 */
    private fun showTyping(user: ChatUser) {
        typingTimers.remove(user.id)?.cancel()
        _state.update { s -> s.copy(typing = s.typing.filter { it.id != user.id } + user) }
        typingTimers[user.id] = viewModelScope.launch {
            delay(5_000)
            hideTyping(user.id)
        }
    }

    private fun hideTyping(userId: Long) {
        typingTimers.remove(userId)?.cancel()
        _state.update { s -> s.copy(typing = s.typing.filter { it.id != userId }) }
    }

    /** 입력창 끝의 "@검색어" 를 "@닉네임 " 으로 바꾼다. user 가 null 이면 @모두 */
    fun pickMention(user: ChatUser?, allLabel: String) {
        val name = user?.nickname ?: allLabel
        _state.update { s ->
            val next = s.input.replace(MENTION_TAIL, "") + "@$name "
            s.copy(input = next.take(4000), mentionPicks = if (user != null) s.mentionPicks + (user.id to user.nickname) else s.mentionPicks)
        }
    }

    fun startReply(message: ChatMessage) = _state.update { it.copy(selected = null, replyTo = message) }
    fun cancelReply() = _state.update { it.copy(replyTo = null) }

    fun react(message: ChatMessage, emoji: String) {
        _state.update { it.copy(selected = null, reactingTo = null) }
        viewModelScope.launch {
            runCatching { chat.react(message.id, emoji) }
                .onSuccess(::replace)
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun chooseReaction(message: ChatMessage?) = _state.update { it.copy(selected = null, reactingTo = message) }

    /** 공지 창 열기 (메시지에서 공지로 올리면 그 글로 시작) */
    fun openNotice(initial: String? = null) = _state.update { it.copy(selected = null, noticeDraft = initial ?: it.room?.notice?.content.orEmpty()) }
    fun closeNotice() = _state.update { it.copy(noticeDraft = null) }

    /** 공지 올리기·고치기 (null 이면 내리기) */
    fun setNotice(content: String?) {
        _state.update { it.copy(noticeDraft = null) }
        viewModelScope.launch {
            runCatching { chat.setNotice(roomId, content) }
                .onSuccess { room -> _state.update { it.copy(room = room) } }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    /** 이 방 알림 끄기·켜기 */
    fun toggleMute() {
        val room = _state.value.room ?: return
        viewModelScope.launch {
            runCatching { chat.setMuted(roomId, !room.muted) }
                .onSuccess { r -> _state.update { it.copy(room = r) } }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    /** 첨부 추가. 서버와 같은 한도(개수·크기·실행 파일)를 먼저 확인하고, 넘으면 안내만 한다 */
    fun addFiles(files: List<PickedFile>) {
        if (_state.value.sending) return
        val next = _state.value.pendingFiles + files
        val problem = ChatFileLimits.check(next)
        if (problem != null) _state.update { it.copy(errorCode = problem) }
        else _state.update { it.copy(pendingFiles = next) }
    }

    fun removeFile(index: Int) = _state.update { s -> s.copy(pendingFiles = s.pendingFiles.filterIndexed { i, _ -> i != index }) }

    /** 글만 있으면 글 메시지, 첨부가 있으면 첨부 + 글을 FILE 메시지 하나로 */
    fun send(allLabel: String) {
        val s = _state.value
        val text = s.input.trim()
        val files = s.pendingFiles
        if ((text.isEmpty() && files.isEmpty()) || s.sending) return
        _state.update { it.copy(sending = true) }
        // 글에 "@닉네임" 이 남아 있는 사람만 멘션 (지웠으면 빼기)
        val mentionIds = s.mentionPicks.filter { (_, name) -> text.contains("@$name") }.keys.toList()
        val mentionAll = s.room?.type == ChatRoomType.GROUP && text.contains("@$allLabel")
        viewModelScope.launch {
            runCatching {
                if (files.isEmpty()) chat.send(roomId, text, s.replyTo?.id, mentionIds, mentionAll)
                else chat.sendFiles(roomId, files.map { UploadFile(it.name, it.mimeType, it.bytes!!) }, text)
            }
                .onSuccess { sent ->
                    _state.update { it.copy(input = "", pendingFiles = emptyList(), sending = false, replyTo = null, mentionPicks = emptyMap()) }
                    append(sent)
                }
                .onFailure { e -> _state.update { it.copy(sending = false, errorCode = e.errorCode) } }
        }
    }

    /** 내가 보낸 메시지는 응답과 실시간 이벤트 양쪽으로 올 수 있어 id 로 한 번만 넣는다 */
    /** 고치거나 지운 메시지를 같은 자리에 바꿔 끼운다 */
    private fun replace(message: ChatMessage) = _state.update { s -> s.copy(messages = s.messages.map { if (it.id == message.id) message else it }) }

    fun select(message: ChatMessage?) = _state.update { it.copy(selected = message) }
    fun startEdit(message: ChatMessage) = _state.update { it.copy(selected = null, editing = message) }
    fun cancelEdit() = _state.update { it.copy(editing = null) }

    fun saveEdit(content: String) {
        val target = _state.value.editing ?: return
        if (content.isBlank() || content.trim() == target.content) return cancelEdit()
        viewModelScope.launch {
            runCatching { chat.edit(target.id, content) }
                .onSuccess { m -> replace(m); _state.update { it.copy(editing = null) } }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun delete(message: ChatMessage) {
        _state.update { it.copy(selected = null) }
        viewModelScope.launch {
            runCatching { chat.delete(message.id) }
                .onSuccess(::replace)
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    private fun append(message: ChatMessage) = _state.update { s ->
        if (s.messages.any { it.id == message.id }) s else s.copy(messages = s.messages + message)
    }

    private fun markRead() {
        val last = _state.value.messages.lastOrNull()?.id ?: return
        viewModelScope.launch {
            runCatching { chat.markRead(roomId, last) }
            badges.refresh()
        }
    }

    fun openInvite() {
        val room = _state.value.room ?: return
        viewModelScope.launch {
            runCatching { teams.detail(room.teamId) }
                .onSuccess { team ->
                    val inRoom = room.members.map { it.id }.toSet()
                    _state.update { it.copy(inviteCandidates = team.members.filter { m -> m.userId !in inRoom }) }
                }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun closeInvite() = _state.update { it.copy(inviteCandidates = null) }

    fun invite(userIds: List<Long>) {
        if (userIds.isEmpty()) return closeInvite()
        viewModelScope.launch {
            runCatching { chat.invite(roomId, userIds); chat.room(roomId) }
                .onSuccess { room -> _state.update { it.copy(room = room, inviteCandidates = null) } }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode, inviteCandidates = null) } }
        }
    }

    fun leave() {
        viewModelScope.launch {
            runCatching { chat.leave(roomId) }
                .onSuccess { _state.update { it.copy(left = true) } }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun errorShown() = _state.update { it.copy(errorCode = null) }

    /** 채널 상시 음성방 들어가기 */
    fun joinVoice() {
        if (_state.value.joiningVoice) return
        _state.update { it.copy(joiningVoice = true) }
        viewModelScope.launch {
            runCatching { meetings.voiceJoinUrl(roomId) }
                .onSuccess { url -> _state.update { it.copy(joiningVoice = false, voiceUrl = url) } }
                .onFailure { e -> _state.update { it.copy(joiningVoice = false, errorCode = e.errorCode) } }
        }
    }

    fun voiceOpened() = _state.update { it.copy(voiceUrl = null) }

    /** 채널 이름 바꾸기 · 카테고리 옮기기 */
    fun updateChannel(name: String, categoryId: Long?) {
        viewModelScope.launch {
            runCatching { chat.updateChannel(roomId, name, categoryId) }
                .onSuccess { room -> _state.update { it.copy(room = room) } }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    private companion object {
        const val PAGE = 50
        /** 입력창 끝의 "@검색어" */
        val MENTION_TAIL = Regex("@[^\\s@]*$")
    }
}

@Composable
fun ChatRoomScreen(
    roomId: Long,
    onBack: () -> Unit,
    /** Android: 앱 안 회의 화면으로 (Windows 는 브라우저) */
    onJoinInApp: (url: String) -> Unit = {},
    /** 채널에 올라온 AI 회의록 카드를 누르면 회의 상세로 */
    onOpenMeeting: (meetingId: Long) -> Unit = {},
    /** 스레드 화면 (시작 글 id) */
    onOpenThread: (rootId: Long) -> Unit = {},
    viewModel: ChatRoomViewModel = koinViewModel(key = "room-$roomId") { parametersOf(roomId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val presence = koinInject<ChatPresence>()
    DisposableEffect(roomId) {
        presence.activeRoomId.value = roomId
        onDispose { if (presence.activeRoomId.value == roomId) presence.activeRoomId.value = null }
    }
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    LaunchedEffect(state.left) { if (state.left) onBack() }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var channelSettings by remember { mutableStateOf(false) }
    var pickInputEmoji by remember { mutableStateOf(false) }
    val allLabel = stringResource(Res.string.chat_mention_all)
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(state.voiceUrl) {
        state.voiceUrl?.let {
            viewModel.voiceOpened()
            if (inAppMeetingSupported) onJoinInApp(it) else uriHandler.openUri(it)
        }
    }

    val room = state.room
    DetailScaffold(
        title = room?.title(state.myId).orEmpty(),
        subtitle = room?.takeIf { it.type == ChatRoomType.GROUP }?.let { r ->
            listOfNotNull(state.categories.firstOrNull { it.id == r.categoryId }?.name, stringResource(Res.string.chat_members, r.members.size)).joinToString(" · ")
        },
        onBack = onBack,
        actions = {
            if (room != null) {
                if (state.canNotice) {
                    IconButton(onClick = { viewModel.openNotice() }) {
                        Icon(Icons.Outlined.Campaign, contentDescription = stringResource(Res.string.chat_notice_title))
                    }
                }
                IconButton(onClick = viewModel::toggleMute) {
                    Icon(
                        if (room.muted) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                        contentDescription = stringResource(if (room.muted) Res.string.chat_unmute else Res.string.chat_mute),
                        tint = if (room.muted) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
                    )
                }
            }
            if (room?.type == ChatRoomType.GROUP) {
                // 디스코드처럼 채널 음성방. 누가 있으면 초록색 + 인원
                IconButton(onClick = viewModel::joinVoice, enabled = !state.joiningVoice) {
                    BadgedBox(badge = { room.voice?.participants?.size?.takeIf { it > 0 }?.let { n -> Badge(containerColor = Color(0xFF16A34A)) { Text("$n") } } }) {
                        Icon(
                            Icons.Outlined.Headphones,
                            contentDescription = stringResource(Res.string.chat_voice_join),
                            tint = if (room.voice != null) Color(0xFF16A34A) else LocalContentColor.current,
                        )
                    }
                }
                if (state.canManageChannel) {
                    IconButton(onClick = { channelSettings = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = stringResource(Res.string.chat_channel_settings))
                    }
                }
                IconButton(onClick = viewModel::openInvite) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = stringResource(Res.string.chat_invite))
                }
                IconButton(onClick = { confirmLeave = true }) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = stringResource(Res.string.chat_leave))
                }
            }
        },
    ) {
        when {
            room == null && state.loadError != null -> ErrorState(state.loadError!!, onRetry = viewModel::load)
            room == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Column(Modifier.fillMaxSize().imePadding()) {
                room.notice?.let { NoticeBar(it, onEdit = if (state.canNotice) ({ viewModel.openNotice() }) else null) }
                MessageList(
                    state,
                    onReachTop = viewModel::loadOlder,
                    onLongPress = viewModel::select,
                    onReact = viewModel::react,
                    onOpenMeeting = onOpenMeeting,
                    onOpenThread = { m -> onOpenThread(m.id) },
                    modifier = Modifier.weight(1f),
                )
                TypingLine(state.typing)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                state.replyTo?.let { ReplyBar(it, onCancel = viewModel::cancelReply) }
                MentionSuggestions(mentionOptions(state, allLabel), allLabel, onPick = { viewModel.pickMention(it, allLabel) })
                InputBar(
                    value = state.input,
                    files = state.pendingFiles,
                    sending = state.sending,
                    onChange = viewModel::setInput,
                    onAttach = viewModel::addFiles,
                    onRemoveFile = viewModel::removeFile,
                    onEmoji = { pickInputEmoji = true },
                    onSend = { viewModel.send(allLabel) },
                )
            }
        }
    }

    if (channelSettings && room != null) {
        ChannelSettingsDialog(
            room = room,
            categories = state.categories,
            onSave = { name, categoryId -> viewModel.updateChannel(name, categoryId); channelSettings = false },
            onDismiss = { channelSettings = false },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            text = { Text(stringResource(Res.string.chat_leave_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; viewModel.leave() }) {
                    Text(stringResource(Res.string.chat_leave), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
    state.selected?.let { m ->
        val mine = m.sender?.id == state.myId
        MessageActionsDialog(
            m,
            MessageActions(
                react = { e -> viewModel.react(m, e) },
                moreEmoji = { viewModel.chooseReaction(m) },
                reply = { viewModel.startReply(m) },
                thread = { viewModel.select(null); onOpenThread(m.id) },
                makeNotice = m.content?.takeIf { state.canNotice && it.isNotBlank() }?.let { c -> { viewModel.openNotice(c) } },
                edit = if (mine && m.type == MessageType.TEXT) ({ viewModel.startEdit(m) }) else null,
                delete = if (mine || state.canModerate) ({ confirmDelete = m; viewModel.select(null) }) else null,
            ),
            onDismiss = { viewModel.select(null) },
        )
    }
    state.reactingTo?.let { m ->
        EmojiPickerDialog(onPick = { e -> viewModel.react(m, e) }, onDismiss = { viewModel.chooseReaction(null) })
    }
    if (pickInputEmoji) {
        EmojiPickerDialog(onPick = { e -> viewModel.setInput(state.input + e); pickInputEmoji = false }, onDismiss = { pickInputEmoji = false })
    }
    state.noticeDraft?.let { draft ->
        NoticeDialog(
            initial = draft,
            canRemove = room?.notice != null,
            onSave = { viewModel.setNotice(it) },
            onRemove = { viewModel.setNotice(null) },
            onDismiss = viewModel::closeNotice,
        )
    }
    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(if (m.sender?.id == state.myId) Res.string.chat_delete_confirm else Res.string.chat_delete_others_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(m); confirmDelete = null }) {
                    Text(stringResource(Res.string.chat_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
    state.editing?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.content ?: "") }
        AlertDialog(
            onDismissRequest = viewModel::cancelEdit,
            title = { Text(stringResource(Res.string.chat_edit)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(4000) },
                    minLines = 2,
                    maxLines = 6,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.saveEdit(text) }, enabled = text.isNotBlank()) { Text(stringResource(Res.string.common_save)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelEdit) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
    state.inviteCandidates?.let { candidates ->
        InviteDialog(candidates, onDismiss = viewModel::closeInvite, onInvite = viewModel::invite)
    }
}

@Composable
private fun MessageList(
    state: ChatRoomUiState,
    onReachTop: () -> Unit,
    onLongPress: (ChatMessage) -> Unit,
    onReact: (ChatMessage, String) -> Unit,
    onOpenMeeting: (Long) -> Unit,
    onOpenThread: (ChatMessage) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val newestFirst = state.messages.asReversed()
    val members = state.room?.members.orEmpty()
    val bubble = BubbleContext(
        myId = state.myId,
        myName = members.firstOrNull { it.id == state.myId }?.nickname,
        names = members.associate { it.id to it.nickname },
        reads = state.room?.reads.orEmpty(),
        onReact = onReact,
        onOpenThread = onOpenThread,
    )

    // reverseLayout: index 0 이 맨 아래(최신). 마지막 항목(가장 오래된 것)이 보이면 이전 페이지 요청
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { last -> if (last != null && last >= listState.layoutInfo.totalItemsCount - 3) onReachTop() }
    }
    // 새 메시지가 오면 맨 아래에 있을 때만 따라 내려간다
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            // reverseLayout 은 아래부터 쌓여야 하므로 간격을 줄 때도 Bottom 정렬을 유지
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
        ) {
            items(newestFirst, key = { it.id }) { message ->
                val mine = message.sender?.id == state.myId
                // 길게 누르면 반응 · 답장 · 공지로 · 고치기 · 지우기
                val actionable = !message.deleted && message.type != MessageType.SYSTEM
                MessageBubble(
                    message,
                    mine = mine,
                    showSender = state.room?.type == ChatRoomType.GROUP,
                    bubble = bubble,
                    onLongPress = if (actionable) ({ onLongPress(message) }) else null,
                    onOpenMeeting = onOpenMeeting,
                )
            }
            if (state.loadingOlder) {
                item { Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp)) } }
            }
        }
    }
}

/** 말풍선마다 같은 정보: 나 · 방 사람 이름 · 읽음 · 반응 누르기 · 스레드 열기 (스레드 화면 안에서는 null) */
internal class BubbleContext(
    val myId: Long,
    val myName: String?,
    val names: Map<Long, String>,
    val reads: List<ReadState>,
    val onReact: (ChatMessage, String) -> Unit,
    val onOpenThread: ((ChatMessage) -> Unit)? = null,
) {
    /** 이 메시지를 아직 안 읽은 사람 수 (보낸 사람 빼고). 스레드 답글은 읽음을 따지지 않는다 */
    fun unread(message: ChatMessage): Int =
        if (message.threadRootId != null) 0
        else reads.count { it.userId != message.sender?.id && (it.lastReadMessageId ?: 0) < message.id }

    fun mentionNames(message: ChatMessage): List<String> =
        message.mentions.mapNotNull { names[it] } + (if (message.mentionAll) ALL_LABELS.toList() else emptyList())
}

/** "@" 뒤에 쓰는 중이면 방 사람(나 빼고) 중 이름이 맞는 사람. 채널이면 맨 앞에 @모두 */
private fun mentionOptions(state: ChatRoomUiState, allLabel: String): List<ChatUser?> {
    val query = Regex("(?:^|\\s)@([^\\s@]*)$").find(state.input)?.groupValues?.get(1) ?: return emptyList()
    val people = state.room?.members.orEmpty().filter { it.id != state.myId && it.nickname.contains(query, ignoreCase = true) }.take(8)
    val all = state.room?.type == ChatRoomType.GROUP && allLabel.startsWith(query, ignoreCase = true)
    return (if (all) listOf<ChatUser?>(null) else emptyList()) + people
}

@Composable
internal fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    showSender: Boolean,
    bubble: BubbleContext,
    onLongPress: (() -> Unit)? = null,
    onOpenMeeting: (Long) -> Unit = {},
) {
    meetingNotesCard(message)?.let { card ->
        MeetingNotesChatCard(card, onClick = { onOpenMeeting(card.meetingId) })
        return
    }
    if (message.type == MessageType.SYSTEM) {
        Text(
            messageText(message),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        return
    }
    val sender = message.sender
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine && sender != null) {
            Avatar(sender.nickname, resolveImageUrl(sender.profileImageUrl), size = 32.dp)
            Spacer(Modifier.width(8.dp))
        }
        if (mine) TimeLabel(message.createdAt, message.edited && !message.deleted, bubble.unread(message), mine)
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 520.dp).weight(1f, fill = false)
                .then(if (onLongPress != null) Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress) else Modifier),
        ) {
            if (!mine && showSender && sender != null) {
                Text(sender.nickname, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!message.deleted) message.replyTo?.let { ReplyQuote(it, onDark = false) }
            if (message.deleted) {
                Text(
                    stringResource(Res.string.chat_deleted_message),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            // 첨부파일(사진 미리보기·파일) 다음에 함께 보낸 글 말풍선
            if (message.files.isNotEmpty()) MessageFiles(message.files, mine)
            val text = message.content
            if (!text.isNullOrEmpty()) {
                if (message.files.isNotEmpty()) Spacer(Modifier.height(4.dp))
                Text(
                    richMessageText(text, bubble.mentionNames(message), bubble.myName, onDark = mine),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            if (!message.deleted) ReactionChips(message.reactions, bubble.myId) { e -> bubble.onReact(message, e) }
            bubble.onOpenThread?.let { open -> ThreadSummary(message, onClick = { open(message) }) }
        }
        if (!mine) TimeLabel(message.createdAt, message.edited && !message.deleted, bubble.unread(message), mine)
    }
}

/** 시작 글 아래 "답글 N개 · 마지막 답글 시각" + 답글 단 사람 얼굴. 누르면 스레드 화면 */
@Composable
private fun ThreadSummary(message: ChatMessage, onClick: () -> Unit) {
    if (message.replyCount <= 0) return
    Row(
        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Forum, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            message.threadUsers.take(3).forEach { u ->
                Box(Modifier.clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)) {
                    Avatar(u.nickname, resolveImageUrl(u.profileImageUrl), size = 20.dp)
                }
            }
        }
        Text(
            stringResource(Res.string.chat_thread_replies, message.replyCount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        message.lastReplyAt?.let {
            Text(formatChatTime(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 보낸 시각 (+ 위에 아직 안 읽은 사람 수, 노란색) */
@Composable
private fun TimeLabel(iso: String, edited: Boolean, unread: Int, mine: Boolean) {
    Column(Modifier.padding(horizontal = 6.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (unread > 0) {
            Text("$unread", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
        }
        Text(
            (if (edited) stringResource(Res.string.chat_edited) + " " else "") + formatChatTime(iso),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun InputBar(
    value: String,
    files: List<PickedFile>,
    sending: Boolean,
    onChange: (String) -> Unit,
    onAttach: (List<PickedFile>) -> Unit,
    onRemoveFile: (Int) -> Unit,
    onEmoji: () -> Unit,
    onSend: () -> Unit,
    /** 입력칸 안내 글 (비우면 "메시지 입력") */
    hint: String? = null,
) {
    val pick = rememberFilePicker(stringResource(Res.string.chat_attach), onAttach)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Column(Modifier.widthIn(max = 960.dp).fillMaxWidth()) {
        // 보내기 전 첨부파일 (이름·크기, X 로 빼기)
        if (files.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                files.forEachIndexed { i, f ->
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.padding(start = 6.dp).widthIn(max = 160.dp)) {
                            Text(f.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatBytes(f.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRemoveFile(i) }, enabled = !sending, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.chat_remove_file), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = pick, enabled = !sending, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.AttachFile, contentDescription = stringResource(Res.string.chat_attach))
            }
            IconButton(onClick = onEmoji, enabled = !sending, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.EmojiEmotions, contentDescription = stringResource(Res.string.chat_emoji))
            }
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                placeholder = { Text(hint ?: stringResource(Res.string.chat_input_hint)) },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                // 키보드(데스크톱·하드웨어): Enter 보내기, Shift+Enter 줄바꿈
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).onPreviewKeyEvent { e ->
                    if (e.key == Key.Enter && e.type == KeyEventType.KeyDown && !e.isShiftPressed) {
                        onSend()
                        true
                    } else {
                        false
                    }
                },
            )
            FilledIconButton(onClick = onSend, enabled = (value.isNotBlank() || files.isNotEmpty()) && !sending, modifier = Modifier.size(48.dp)) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(Res.string.chat_send))
            }
        }
      }
    }
}

@Composable
private fun InviteDialog(candidates: List<TeamMember>, onDismiss: () -> Unit, onInvite: (List<Long>) -> Unit) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.chat_invite)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(Res.string.chat_invite_none))
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(candidates, key = { it.userId }) { member ->
                        MemberRow(member, checked = member.userId in selected) {
                            selected = if (member.userId in selected) selected - member.userId else selected + member.userId
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onInvite(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(Res.string.chat_invite), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}
