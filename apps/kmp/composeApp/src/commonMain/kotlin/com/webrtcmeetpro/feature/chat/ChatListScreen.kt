package com.webrtcmeetpro.feature.chat

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
)

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

    ScreenList(refreshing = state.teams.loading && teams != null, onRefresh = viewModel::refresh) {
        item {
            ScreenTitle(stringResource(Res.string.chat_title)) {
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
                        val sections = state.categories.map { c -> c.name to channels.filter { it.categoryId == c.id } } +
                            listOf(
                                "#" to channels.filter { it.categoryId == null || it.categoryId !in known },
                                "@" to rooms.filter { it.type == ChatRoomType.DIRECT },
                            )
                        sections.filter { it.second.isNotEmpty() }.forEachIndexed { i, (title, list) ->
                            item(key = "h$i") {
                                Text(
                                    when (title) {
                                        "#" -> stringResource(Res.string.chat_channels)
                                        "@" -> stringResource(Res.string.chat_directs)
                                        else -> title
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                                )
                            }
                            items(list, key = { it.id }) { room -> RoomRow(room, state.myId) { onOpenRoom(room.id) } }
                        }
                    }
                }
            }
        }
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
