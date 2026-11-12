package com.webrtcmeetpro.feature.chat

import com.webrtcmeetpro.resources.chat_voice_now
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import com.webrtcmeetpro.core.model.ChatMessage
import com.webrtcmeetpro.core.model.ChatUser
import com.webrtcmeetpro.core.model.PresenceStatus
import com.webrtcmeetpro.resources.chat_muted
import com.webrtcmeetpro.resources.chat_search
import com.webrtcmeetpro.resources.chat_search_empty
import com.webrtcmeetpro.resources.chat_search_hint
import com.webrtcmeetpro.resources.chat_typing_short
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.webrtcmeetpro.ui.ErrorSnackbarEffect
import com.webrtcmeetpro.resources.chat_new_category
import com.webrtcmeetpro.resources.chat_rename_category
import com.webrtcmeetpro.resources.chat_delete_category
import com.webrtcmeetpro.resources.chat_delete_category_confirm
import com.webrtcmeetpro.resources.chat_category_name
import com.webrtcmeetpro.resources.chat_delete
import com.webrtcmeetpro.resources.common_cancel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.ChatRepository
import com.webrtcmeetpro.core.data.RealtimeRepository
import com.webrtcmeetpro.core.data.TeamRepository
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.designsystem.component.Avatar
import com.webrtcmeetpro.core.designsystem.component.EmptyState
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.designsystem.component.SecondaryButton
import com.webrtcmeetpro.core.model.ChatRoom
import com.webrtcmeetpro.resources.chat_directs
import com.webrtcmeetpro.resources.chat_channels
import com.webrtcmeetpro.core.model.ChatCategory
import com.webrtcmeetpro.core.model.ChatRoomType
import com.webrtcmeetpro.core.model.MessageType
import com.webrtcmeetpro.core.model.TeamSummary
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.platform.formatChatTime
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.chat_empty
import com.webrtcmeetpro.resources.chat_empty_body
import com.webrtcmeetpro.resources.chat_new
import com.webrtcmeetpro.resources.chat_no_message
import com.webrtcmeetpro.resources.chat_title
import com.webrtcmeetpro.resources.home_no_teams
import com.webrtcmeetpro.resources.home_no_teams_body
import com.webrtcmeetpro.ui.ErrorState
import com.webrtcmeetpro.ui.Loadable
import com.webrtcmeetpro.ui.ScreenList
import com.webrtcmeetpro.ui.ScreenTitle
import com.webrtcmeetpro.ui.loadingItem
import com.webrtcmeetpro.ui.resolveImageUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class ChatListUiState(
    val teams: Loadable<List<TeamSummary>> = Loadable(),
    val selectedTeamId: Long? = null,
    val rooms: Loadable<List<ChatRoom>> = Loadable(loading = false),
    /** 채널을 묶는 카테고리(부서) */
    val categories: List<ChatCategory> = emptyList(),
    val myId: Long = 0,
    val errorCode: String? = null,
    /** 접속 중·자리 비움인 사람 (없으면 접속 안 함) */
    val presence: Map<Long, PresenceStatus> = emptyMap(),
    /** 방마다 지금 입력 중인 사람 */
    val typing: Map<Long, List<ChatUser>> = emptyMap(),
    /** 메시지 검색 결과 (null = 아직 안 찾음) */
    val searchResults: List<ChatMessage>? = null,
    val searching: Boolean = false,
) {
    /** 고른 워크스페이스에서 카테고리·채널을 만들고 고칠 수 있는지 (OWNER·ADMIN) */
    val canManage: Boolean get() = teams.data?.firstOrNull { it.id == selectedTeamId }?.canCreateGroupRoom == true
}

class ChatListViewModel(
    private val teams: TeamRepository,
    private val chat: ChatRepository,
    private val users: UserRepository,
    realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()
    private var roomsJob: Job? = null

    init {
        refresh()
        // 새 메시지·새 방 → 지금 보고 있는 팀이면 목록(마지막 메시지, 안 읽은 수) 갱신
        viewModelScope.launch {
            realtime.chatEvents.collect { event ->
                if (event.teamId != _state.value.selectedTeamId) return@collect
                val typingUser = event.typing
                when {
                    event.type == "TYPING" -> if (typingUser != null && typingUser.id != _state.value.myId) showTyping(event.roomId, typingUser)
                    // 남이 읽은 것은 목록에 영향 없음
                    event.type == "READ" -> if (event.read?.userId == _state.value.myId) loadRooms(silent = true)
                    else -> {
                        event.message?.sender?.let { if (event.type == "MESSAGE") hideTyping(event.roomId, it.id) }
                        loadRooms(silent = true)
                    }
                }
            }
        }
        viewModelScope.launch {
            realtime.presence.collect { p -> _state.update { it.copy(presence = it.presence + (p.userId to p.status)) } }
        }
    }

    private val typingTimers = mutableMapOf<Pair<Long, Long>, Job>()

    private fun showTyping(roomId: Long, user: ChatUser) {
        typingTimers.remove(roomId to user.id)?.cancel()
        _state.update { s -> s.copy(typing = s.typing + (roomId to (s.typing[roomId].orEmpty().filter { it.id != user.id } + user))) }
        typingTimers[roomId to user.id] = viewModelScope.launch {
            delay(5_000)
            hideTyping(roomId, user.id)
        }
    }

    private fun hideTyping(roomId: Long, userId: Long) {
        typingTimers.remove(roomId to userId)?.cancel()
        _state.update { s -> s.copy(typing = s.typing + (roomId to s.typing[roomId].orEmpty().filter { it.id != userId })) }
    }

    private var searchJob: Job? = null

    /** 글을 쓰고 잠깐 멈추면 찾는다 (2글자부터) */
    fun search(query: String) {
        val teamId = _state.value.selectedTeamId ?: return
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(searchResults = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(searching = true) }
            runCatching { chat.search(teamId, query) }
                .onSuccess { list -> _state.update { it.copy(searchResults = list, searching = false) } }
                .onFailure { e -> _state.update { it.copy(searching = false, errorCode = e.errorCode) } }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searchResults = null, searching = false) }
    }

    fun refresh() {
        _state.update { it.copy(teams = it.teams.copy(loading = true, errorCode = null)) }
        viewModelScope.launch {
            runCatching {
                val me = users.me()
                me.id to teams.teams()
            }.onSuccess { (myId, list) ->
                val selected = _state.value.selectedTeamId?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
                _state.update { it.copy(teams = Loadable(list, loading = false), selectedTeamId = selected, myId = myId) }
                loadRooms()
            }.onFailure { e -> _state.update { it.copy(teams = it.teams.copy(loading = false, errorCode = e.errorCode)) } }
        }
    }

    fun selectTeam(teamId: Long) {
        if (teamId == _state.value.selectedTeamId) return
        _state.update { it.copy(selectedTeamId = teamId, rooms = Loadable(), presence = emptyMap(), typing = emptyMap()) }
        loadRooms()
    }

    fun createCategory(name: String) = categoryAction { teamId -> chat.createCategory(teamId, name) }
    fun renameCategory(id: Long, name: String) = categoryAction { chat.renameCategory(id, name) }
    fun deleteCategory(id: Long) = categoryAction { chat.deleteCategory(id) }
    fun errorShown() = _state.update { it.copy(errorCode = null) }

    private fun categoryAction(block: suspend (teamId: Long) -> Unit) {
        val teamId = _state.value.selectedTeamId ?: return
        viewModelScope.launch {
            runCatching { block(teamId) }
                .onSuccess { loadRooms(silent = true) }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    private fun loadRooms(silent: Boolean = false) {
        val teamId = _state.value.selectedTeamId ?: return
        if (!silent) _state.update { it.copy(rooms = it.rooms.copy(loading = true, errorCode = null)) }
        roomsJob?.cancel()
        roomsJob = viewModelScope.launch {
            runCatching { chat.rooms(teamId) to runCatching { chat.categories(teamId) }.getOrDefault(emptyList()) }
                .onSuccess { (list, cats) -> _state.update { it.copy(rooms = Loadable(list, loading = false), categories = cats) } }
                .onFailure { e -> _state.update { it.copy(rooms = it.rooms.copy(loading = false, errorCode = e.errorCode)) } }
            if (!silent) {
                runCatching { chat.presence(teamId) }
                    .onSuccess { list -> _state.update { it.copy(presence = list.associate { p -> p.userId to p.status }) } }
            }
        }
    }
}

@Composable
fun ChatListScreen(
    onOpenRoom: (Long) -> Unit,
    onNewChat: (teamId: Long) -> Unit,
    viewModel: ChatListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val teams = state.teams.data
    val rooms = state.rooms.data
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    var creatingCategory by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChatCategory?>(null) }
    var deleting by remember { mutableStateOf<ChatCategory?>(null) }
    var searchOpen by remember { mutableStateOf(false) }

    ScreenList(refreshing = state.teams.loading && teams != null, onRefresh = viewModel::refresh) {
        item {
            ScreenTitle(stringResource(Res.string.chat_title)) {
                if (state.selectedTeamId != null) {
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(Res.string.chat_search))
                    }
                }
                if (state.canManage) {
                    IconButton(onClick = { creatingCategory = true }) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = stringResource(Res.string.chat_new_category))
                    }
                }
                state.selectedTeamId?.let { teamId ->
                    SecondaryButton(stringResource(Res.string.chat_new), onClick = { onNewChat(teamId) }, icon = Icons.Outlined.Edit)
                }
            }
        }
        when {
            teams == null -> state.teams.errorCode?.let { code -> item { ErrorState(code, onRetry = viewModel::refresh) } } ?: loadingItem()
            teams.isEmpty() -> item {
                LimeetCard(Modifier.fillMaxWidth()) {
                    EmptyState(Icons.Outlined.Groups, stringResource(Res.string.home_no_teams), body = stringResource(Res.string.home_no_teams_body))
                }
            }
            else -> {
                if (teams.size > 1) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            teams.forEach { team ->
                                FilterChip(
                                    selected = team.id == state.selectedTeamId,
                                    onClick = { viewModel.selectTeam(team.id) },
                                    label = { Text(team.name) },
                                )
                            }
                        }
                    }
                }
                when {
                    rooms == null -> state.rooms.errorCode?.let { code -> item { ErrorState(code, onRetry = viewModel::refresh) } } ?: loadingItem()
                    rooms.isEmpty() -> item {
                        LimeetCard(Modifier.fillMaxWidth()) {
                            EmptyState(
                                Icons.Outlined.ChatBubbleOutline,
                                stringResource(Res.string.chat_empty),
                                body = stringResource(Res.string.chat_empty_body),
                            )
                        }
                    }
                    else -> {
                        // 디스코드처럼: 카테고리(부서) → 채널, 카테고리 없는 채널, 1:1 대화
                        val channels = rooms.filter { it.type == ChatRoomType.GROUP }
                        val known = state.categories.map { it.id }.toSet()
                        // (제목, 방들, 카테고리). 관리자는 빈 카테고리도 보여서 고치거나 지울 수 있다
                        val sections = state.categories.map { c -> Triple(c.name, channels.filter { it.categoryId == c.id }, c) } +
                            listOf(
                                Triple("#", channels.filter { it.categoryId == null || it.categoryId !in known }, null),
                                Triple("@", rooms.filter { it.type == ChatRoomType.DIRECT }, null),
                            )
                        sections.filter { it.second.isNotEmpty() || (state.canManage && it.third != null) }.forEachIndexed { i, (title, list, category) ->
                            item(key = "h$i") {
                                Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        when (title) {
                                            "#" -> stringResource(Res.string.chat_channels)
                                            "@" -> stringResource(Res.string.chat_directs)
                                            else -> title
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (state.canManage && category != null) {
                                        IconButton(onClick = { renaming = category }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.chat_rename_category), modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { deleting = category }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.chat_delete_category), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            items(list, key = { it.id }) { room ->
                                val partner = room.partner(state.myId)
                                RoomRow(
                                    room,
                                    state.myId,
                                    presence = partner?.let { state.presence[it.id] ?: PresenceStatus.OFFLINE },
                                    typing = state.typing[room.id].orEmpty(),
                                ) { onOpenRoom(room.id) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (searchOpen) {
        SearchDialog(
            state,
            onQuery = viewModel::search,
            onOpen = { roomId -> searchOpen = false; viewModel.clearSearch(); onOpenRoom(roomId) },
            onDismiss = { searchOpen = false; viewModel.clearSearch() },
        )
    }
    if (creatingCategory) {
        NameDialog(
            title = stringResource(Res.string.chat_new_category),
            label = stringResource(Res.string.chat_category_name),
            onConfirm = { viewModel.createCategory(it); creatingCategory = false },
            onDismiss = { creatingCategory = false },
        )
    }
    renaming?.let { c ->
        NameDialog(
            title = stringResource(Res.string.chat_rename_category),
            label = stringResource(Res.string.chat_category_name),
            initial = c.name,
            onConfirm = { viewModel.renameCategory(c.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(Res.string.chat_delete_category_confirm, c.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCategory(c.id); deleting = null }) {
                    Text(stringResource(Res.string.chat_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}

/** 워크스페이스 메시지 검색: 결과를 누르면 그 방으로 */
@Composable
private fun SearchDialog(state: ChatListUiState, onQuery: (String) -> Unit, onOpen: (Long) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val rooms = state.rooms.data.orEmpty().associateBy { it.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.chat_search)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(100); onQuery(query) },
                    placeholder = { Text(stringResource(Res.string.chat_search_hint)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val results = state.searchResults
                when {
                    state.searching -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    results == null -> {}
                    results.isEmpty() -> Text(stringResource(Res.string.chat_search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                    else -> LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(results, key = { it.id }) { m ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(m.roomId) }.padding(horizontal = 8.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    listOfNotNull(rooms[m.roomId]?.title(state.myId), m.sender?.nickname, formatChatTime(m.createdAt)).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(messageText(m), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** presence: 1:1 방 상대의 접속 상태 (채널이면 null) */
@Composable
private fun RoomRow(room: ChatRoom, myId: Long, presence: PresenceStatus?, typing: List<ChatUser>, onClick: () -> Unit) {
    LimeetCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                RoomAvatar(room, myId)
                presence?.let { PresenceDot(it, Modifier.align(Alignment.BottomEnd)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        room.title(myId),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (room.type == ChatRoomType.GROUP) {
                        Text(
                            "  ${room.members.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (room.muted) {
                        Icon(
                            Icons.Outlined.NotificationsOff,
                            contentDescription = stringResource(Res.string.chat_muted),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        )
                    }
                }
                val last = room.lastMessage
                if (typing.isNotEmpty()) {
                    // 누가 입력 중이면 마지막 메시지 대신
                    Text(
                        stringResource(Res.string.chat_typing_short, typing.first().nickname),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        last?.let { messageText(it) } ?: stringResource(Res.string.chat_no_message),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = if (last == null || last.type == MessageType.SYSTEM) FontStyle.Italic else FontStyle.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 채널 음성방에 누가 있으면 (디스코드처럼)
                room.voice?.participants?.takeIf { it.isNotEmpty() }?.let { people ->
                    Text(
                        "🔊 " + stringResource(Res.string.chat_voice_now, people.size) + " · " + people.take(3).joinToString(", ") { it.nickname },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF16A34A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    room.lastMessageAt?.let(::formatChatTime).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (room.unreadCount > 0) {
                    Box(
                        // 알림 끈 방은 회색 배지
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (room.muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (room.unreadCount > 99) "99+" else room.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** 1:1 은 상대 프로필, 그룹은 사람들 아이콘 */
@Composable
fun RoomAvatar(room: ChatRoom, myId: Long, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val partner = room.partner(myId)
    if (partner != null) {
        Avatar(partner.nickname, resolveImageUrl(partner.profileImageUrl), size = size)
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
