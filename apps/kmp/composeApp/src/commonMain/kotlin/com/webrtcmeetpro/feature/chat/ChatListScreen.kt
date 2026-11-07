package com.webrtcmeetpro.feature.chat

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
                if (event.teamId == _state.value.selectedTeamId) loadRooms(silent = true)
            }
        }
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
        _state.update { it.copy(selectedTeamId = teamId, rooms = Loadable()) }
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

    ScreenList(refreshing = state.teams.loading && teams != null, onRefresh = viewModel::refresh) {
        item {
            ScreenTitle(stringResource(Res.string.chat_title)) {
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
                            items(list, key = { it.id }) { room -> RoomRow(room, state.myId) { onOpenRoom(room.id) } }
                        }
                    }
                }
            }
        }
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

@Composable
private fun RoomRow(room: ChatRoom, myId: Long, onClick: () -> Unit) {
    LimeetCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoomAvatar(room, myId)
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
                }
                val last = room.lastMessage
                Text(
                    last?.let { messageText(it) } ?: stringResource(Res.string.chat_no_message),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = if (last == null || last.type == MessageType.SYSTEM) FontStyle.Italic else FontStyle.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    room.lastMessageAt?.let(::formatChatTime).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (room.unreadCount > 0) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 2.dp),
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
