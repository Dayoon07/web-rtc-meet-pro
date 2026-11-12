package com.webrtcmeetpro.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.ChatRepository
import com.webrtcmeetpro.core.data.RealtimeRepository
import com.webrtcmeetpro.core.data.TeamRepository
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.model.ChatMessage
import com.webrtcmeetpro.core.model.ChatRoom
import com.webrtcmeetpro.core.model.ChatRoomType
import com.webrtcmeetpro.core.model.ChatUser
import com.webrtcmeetpro.core.model.MessageType
import com.webrtcmeetpro.core.network.UploadFile
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.platform.ChatFileLimits
import com.webrtcmeetpro.platform.PickedFile
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.chat_delete
import com.webrtcmeetpro.resources.chat_delete_confirm
import com.webrtcmeetpro.resources.chat_delete_others_confirm
import com.webrtcmeetpro.resources.chat_edit
import com.webrtcmeetpro.resources.chat_mention_all
import com.webrtcmeetpro.resources.chat_thread_input_hint
import com.webrtcmeetpro.resources.chat_thread_no_replies
import com.webrtcmeetpro.resources.chat_thread_replies
import com.webrtcmeetpro.resources.chat_thread_title
import com.webrtcmeetpro.resources.common_cancel
import com.webrtcmeetpro.resources.common_save
import com.webrtcmeetpro.ui.DetailScaffold
import com.webrtcmeetpro.ui.ErrorSnackbarEffect
import com.webrtcmeetpro.ui.ErrorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class ThreadUiState(
    val myId: Long = 0,
    val room: ChatRoom? = null,
    /** 스레드 시작 글 (지워졌으면 "삭제된 메시지" + 입력창 없음) */
    val root: ChatMessage? = null,
    /** 답글 (오래된 것 → 최신) */
    val replies: List<ChatMessage> = emptyList(),
    val loadError: String? = null,
    val input: String = "",
    val pendingFiles: List<PickedFile> = emptyList(),
    val sending: Boolean = false,
    val errorCode: String? = null,
    val canModerate: Boolean = false,
    val selected: ChatMessage? = null,
    val editing: ChatMessage? = null,
    val reactingTo: ChatMessage? = null,
    /** 입력창에서 고른 @멘션 (id → 닉네임) */
    val mentionPicks: Map<Long, String> = emptyMap(),
)

/**
 * 스레드 화면 하나 (슬랙·매터모스트처럼 메시지 아래에 이어지는 답글 대화).
 * rootId 가 답글 id 여도 서버가 시작 글을 돌려준다. 실시간으로 그 스레드의 새 답글·고친 글을 받는다
 */
class ThreadViewModel(
    private val rootId: Long,
    private val chat: ChatRepository,
    private val teams: TeamRepository,
    private val users: UserRepository,
    realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    /** 진짜 시작 글 id (주소로 답글 id 가 와도) */
    val realRootId: Long get() = _state.value.root?.id ?: rootId

    init {
        load()
        viewModelScope.launch {
            realtime.chatEvents.collect { event ->
                val m = event.message ?: return@collect
                val root = realRootId
                when {
                    event.type == "MESSAGE" && m.threadRootId == root -> append(m)
                    event.type == "MESSAGE_UPDATED" && m.id == root -> _state.update { it.copy(root = m) }
                    event.type == "MESSAGE_UPDATED" && m.threadRootId == root -> replace(m)
                }
            }
        }
    }

    fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            runCatching {
                val thread = chat.thread(rootId)
                Triple(users.me().id, thread, chat.room(thread.root.roomId))
            }.onSuccess { (myId, thread, room) ->
                _state.update { it.copy(myId = myId, root = thread.root, replies = thread.replies, room = room) }
                runCatching { teams.detail(room.teamId) }
                    .onSuccess { t -> _state.update { it.copy(canModerate = "DELETE_ANY_MESSAGE" in t.team.myPermissions) } }
            }.onFailure { e -> _state.update { it.copy(loadError = e.errorCode) } }
        }
    }

    fun setInput(value: String) = _state.update { it.copy(input = value.take(4000)) }

    /** 입력창 끝의 "@검색어" 를 "@닉네임 " 으로. user 가 null 이면 @모두 */
    fun pickMention(user: ChatUser?, allLabel: String) {
        val name = user?.nickname ?: allLabel
        _state.update { s ->
            val next = s.input.replace(MENTION_TAIL, "") + "@$name "
            s.copy(input = next.take(4000), mentionPicks = if (user != null) s.mentionPicks + (user.id to user.nickname) else s.mentionPicks)
        }
    }

    fun addFiles(files: List<PickedFile>) {
        if (_state.value.sending) return
        val next = _state.value.pendingFiles + files
        val problem = ChatFileLimits.check(next)
        if (problem != null) _state.update { it.copy(errorCode = problem) } else _state.update { it.copy(pendingFiles = next) }
    }

    fun removeFile(index: Int) = _state.update { s -> s.copy(pendingFiles = s.pendingFiles.filterIndexed { i, _ -> i != index }) }

    /** 이 스레드에 답글 (글만 있으면 글, 첨부가 있으면 첨부 + 글) */
    fun send(allLabel: String) {
        val s = _state.value
        val root = s.root ?: return
        val text = s.input.trim()
        val files = s.pendingFiles
        if ((text.isEmpty() && files.isEmpty()) || s.sending) return
        _state.update { it.copy(sending = true) }
        val mentionIds = s.mentionPicks.filter { (_, name) -> text.contains("@$name") }.keys.toList()
        val mentionAll = s.room?.type == ChatRoomType.GROUP && text.contains("@$allLabel")
        viewModelScope.launch {
            runCatching {
                if (files.isEmpty()) chat.send(root.roomId, text, mentionIds = mentionIds, mentionAll = mentionAll, threadRootId = root.id)
                else chat.sendFiles(root.roomId, files.map { UploadFile(it.name, it.mimeType, it.bytes!!) }, text, threadRootId = root.id)
            }
                .onSuccess { sent ->
                    _state.update { it.copy(input = "", pendingFiles = emptyList(), sending = false, mentionPicks = emptyMap()) }
                    append(sent)
                }
                .onFailure { e -> _state.update { it.copy(sending = false, errorCode = e.errorCode) } }
        }
    }

    fun react(message: ChatMessage, emoji: String) {
        _state.update { it.copy(selected = null, reactingTo = null) }
        viewModelScope.launch {
            runCatching { chat.react(message.id, emoji) }
                .onSuccess(::replaceAny)
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun chooseReaction(message: ChatMessage?) = _state.update { it.copy(selected = null, reactingTo = message) }
    fun select(message: ChatMessage?) = _state.update { it.copy(selected = message) }
    fun startEdit(message: ChatMessage) = _state.update { it.copy(selected = null, editing = message) }
    fun cancelEdit() = _state.update { it.copy(editing = null) }

    fun saveEdit(content: String) {
        val target = _state.value.editing ?: return
        if (content.isBlank() || content.trim() == target.content) return cancelEdit()
        viewModelScope.launch {
            runCatching { chat.edit(target.id, content) }
                .onSuccess { m -> replaceAny(m); _state.update { it.copy(editing = null) } }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun delete(message: ChatMessage) {
        _state.update { it.copy(selected = null) }
        viewModelScope.launch {
            runCatching { chat.delete(message.id) }
                .onSuccess(::replaceAny)
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun errorShown() = _state.update { it.copy(errorCode = null) }

    private fun append(message: ChatMessage) = _state.update { s ->
        if (s.replies.any { it.id == message.id }) s else s.copy(replies = s.replies + message)
    }

    private fun replace(message: ChatMessage) = _state.update { s -> s.copy(replies = s.replies.map { if (it.id == message.id) message else it }) }

    /** 시작 글이든 답글이든 같은 id 를 바꿔 끼운다 */
    private fun replaceAny(message: ChatMessage) =
        if (message.id == realRootId) _state.update { it.copy(root = message) } else replace(message)

    private companion object {
        val MENTION_TAIL = Regex("@[^\\s@]*$")
    }
}

@Composable
fun ThreadScreen(
    rootId: Long,
    onBack: () -> Unit,
    viewModel: ThreadViewModel = koinViewModel(key = "thread-$rootId") { parametersOf(rootId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val presence = koinInject<ChatPresence>()
    // 보고 있는 스레드의 답글 알림은 시스템 알림으로 띄우지 않는다
    DisposableEffect(state.root?.id) {
        val id = state.root?.id
        presence.activeThreadId.value = id
        onDispose { if (presence.activeThreadId.value == id) presence.activeThreadId.value = null }
    }
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    val allLabel = stringResource(Res.string.chat_mention_all)
    var pickInputEmoji by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ChatMessage?>(null) }
    val room = state.room
    val root = state.root

    DetailScaffold(
        title = stringResource(Res.string.chat_thread_title),
        subtitle = room?.let { r -> if (r.type == ChatRoomType.GROUP) "# ${r.name.orEmpty()}" else r.title(state.myId) },
        onBack = onBack,
    ) {
        when {
            root == null && state.loadError != null -> ErrorState(state.loadError!!, onRetry = viewModel::load)
            root == null || room == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Column(Modifier.fillMaxSize().imePadding()) {
                ThreadMessages(state, root, room, onLongPress = viewModel::select, onReact = viewModel::react, modifier = Modifier.weight(1f))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (!root.deleted) {
                    MentionSuggestions(threadMentionOptions(state, allLabel), allLabel, onPick = { viewModel.pickMention(it, allLabel) })
                    InputBar(
                        value = state.input,
                        files = state.pendingFiles,
                        sending = state.sending,
                        onChange = viewModel::setInput,
                        onAttach = viewModel::addFiles,
                        onRemoveFile = viewModel::removeFile,
                        onEmoji = { pickInputEmoji = true },
                        onSend = { viewModel.send(allLabel) },
                        hint = stringResource(Res.string.chat_thread_input_hint),
                    )
                }
            }
        }
    }

    state.selected?.let { m ->
        val mine = m.sender?.id == state.myId
        MessageActionsDialog(
            m,
            MessageActions(
                react = { e -> viewModel.react(m, e) },
                moreEmoji = { viewModel.chooseReaction(m) },
                reply = null,
                makeNotice = null,
                edit = if (mine && m.type == MessageType.TEXT) ({ viewModel.startEdit(m) }) else null,
                delete = if (mine || state.canModerate) ({ confirmDelete = m; viewModel.select(null) }) else null,
            ),
            onDismiss = { viewModel.select(null) },
        )
    }
    state.reactingTo?.let { m -> EmojiPickerDialog(onPick = { e -> viewModel.react(m, e) }, onDismiss = { viewModel.chooseReaction(null) }) }
    if (pickInputEmoji) {
        EmojiPickerDialog(onPick = { e -> viewModel.setInput(state.input + e); pickInputEmoji = false }, onDismiss = { pickInputEmoji = false })
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
                OutlinedTextField(value = text, onValueChange = { text = it.take(4000) }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = { viewModel.saveEdit(text) }, enabled = text.isNotBlank()) { Text(stringResource(Res.string.common_save)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelEdit) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}

/** 시작 글 · "답글 N개" 줄 · 답글. 새 답글이 오면 맨 아래로 */
@Composable
private fun ThreadMessages(
    state: ThreadUiState,
    root: ChatMessage,
    room: ChatRoom,
    onLongPress: (ChatMessage) -> Unit,
    onReact: (ChatMessage, String) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val bubble = BubbleContext(
        myId = state.myId,
        myName = room.members.firstOrNull { it.id == state.myId }?.nickname,
        names = room.members.associate { it.id to it.nickname },
        reads = emptyList(),
        onReact = onReact,
    )
    LaunchedEffect(state.replies.size) {
        if (state.replies.isNotEmpty()) listState.animateScrollToItem(state.replies.size + 1)
    }
    val actionable = { m: ChatMessage -> !m.deleted && m.type != MessageType.SYSTEM }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "root") {
                MessageBubble(
                    root,
                    mine = root.sender?.id == state.myId,
                    showSender = true,
                    bubble = bubble,
                    onLongPress = if (actionable(root)) ({ onLongPress(root) }) else null,
                )
            }
            item(key = "divider") {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (root.replyCount > 0) stringResource(Res.string.chat_thread_replies, root.replyCount) else stringResource(Res.string.chat_thread_no_replies),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            items(state.replies, key = { it.id }) { m ->
                MessageBubble(
                    m,
                    mine = m.sender?.id == state.myId,
                    showSender = true,
                    bubble = bubble,
                    onLongPress = if (actionable(m)) ({ onLongPress(m) }) else null,
                )
            }
        }
    }
}

/** "@" 뒤에 쓰는 중이면 방 사람(나 빼고) 중 이름이 맞는 사람. 채널이면 맨 앞에 @모두 */
private fun threadMentionOptions(state: ThreadUiState, allLabel: String): List<ChatUser?> {
    val query = Regex("(?:^|\\s)@([^\\s@]*)$").find(state.input)?.groupValues?.get(1) ?: return emptyList()
    val people = state.room?.members.orEmpty().filter { it.id != state.myId && it.nickname.contains(query, ignoreCase = true) }.take(8)
    val all = state.room?.type == ChatRoomType.GROUP && allLabel.startsWith(query, ignoreCase = true)
    return (if (all) listOf<ChatUser?>(null) else emptyList()) + people
}
