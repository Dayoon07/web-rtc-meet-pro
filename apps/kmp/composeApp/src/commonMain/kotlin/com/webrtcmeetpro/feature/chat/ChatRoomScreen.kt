package com.webrtcmeetpro.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/** 지금 열려 있는 채팅방. 그 방의 새 메시지는 시스템 알림을 띄우지 않는다 */
class ChatPresence {
    val activeRoomId = MutableStateFlow<Long?>(null)
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
    val sending: Boolean = false,
    val errorCode: String? = null,
    val inviteCandidates: List<TeamMember>? = null,
    val left: Boolean = false,
)

class ChatRoomViewModel(
    private val roomId: Long,
    private val chat: ChatRepository,
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
                if (event.type == "MESSAGE" && message != null) {
                    append(message)
                    markRead()
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

    fun setInput(value: String) = _state.update { it.copy(input = value.take(4000)) }

    fun send() {
        val s = _state.value
        val text = s.input.trim()
        if (text.isEmpty() || s.sending) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            runCatching { chat.send(roomId, text) }
                .onSuccess { sent ->
                    _state.update { it.copy(input = "", sending = false) }
                    append(sent)
                }
                .onFailure { e -> _state.update { it.copy(sending = false, errorCode = e.errorCode) } }
        }
    }

    /** 내가 보낸 메시지는 응답과 실시간 이벤트 양쪽으로 올 수 있어 id 로 한 번만 넣는다 */
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

    private companion object {
        const val PAGE = 50
    }
}

@Composable
fun ChatRoomScreen(
    roomId: Long,
    onBack: () -> Unit,
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

    val room = state.room
    DetailScaffold(
        title = room?.title(state.myId).orEmpty(),
        subtitle = room?.takeIf { it.type == ChatRoomType.GROUP }?.let { stringResource(Res.string.chat_members, it.members.size) },
        onBack = onBack,
        actions = {
            if (room?.type == ChatRoomType.GROUP) {
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
                MessageList(state, onReachTop = viewModel::loadOlder, modifier = Modifier.weight(1f))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InputBar(state.input, state.sending, viewModel::setInput, viewModel::send)
            }
        }
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
    state.inviteCandidates?.let { candidates ->
        InviteDialog(candidates, onDismiss = viewModel::closeInvite, onInvite = viewModel::invite)
    }
}

@Composable
private fun MessageList(state: ChatRoomUiState, onReachTop: () -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    val newestFirst = state.messages.asReversed()

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
                MessageBubble(message, mine = message.sender?.id == state.myId, showSender = state.room?.type == ChatRoomType.GROUP)
            }
            if (state.loadingOlder) {
                item { Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp)) } }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, mine: Boolean, showSender: Boolean) {
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
        if (mine) TimeLabel(message.createdAt)
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 520.dp).weight(1f, fill = false)) {
            if (!mine && showSender && sender != null) {
                Text(sender.nickname, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
        if (!mine) TimeLabel(message.createdAt)
    }
}

@Composable
private fun TimeLabel(iso: String) {
    Text(
        formatChatTime(iso),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun InputBar(value: String, sending: Boolean, onChange: (String) -> Unit, onSend: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                placeholder = { Text(stringResource(Res.string.chat_input_hint)) },
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
            FilledIconButton(onClick = onSend, enabled = value.isNotBlank() && !sending, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(Res.string.chat_send))
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
