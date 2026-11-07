package com.webrtcmeetpro.feature.chat

import com.webrtcmeetpro.core.model.ChatCategory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.ChatRepository
import com.webrtcmeetpro.core.data.TeamRepository
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.designsystem.component.Avatar
import com.webrtcmeetpro.core.designsystem.component.PrimaryButton
import com.webrtcmeetpro.core.model.TeamDetail
import com.webrtcmeetpro.core.model.TeamMember
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.chat_direct
import com.webrtcmeetpro.resources.chat_group
import com.webrtcmeetpro.resources.chat_group_create
import com.webrtcmeetpro.resources.chat_group_name
import com.webrtcmeetpro.resources.chat_group_owner_only
import com.webrtcmeetpro.resources.chat_new
import com.webrtcmeetpro.resources.chat_no_teammates
import com.webrtcmeetpro.ui.DetailScaffold
import com.webrtcmeetpro.ui.ErrorSnackbarEffect
import com.webrtcmeetpro.ui.ErrorState
import com.webrtcmeetpro.ui.resolveImageUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class NewChatUiState(
    val team: TeamDetail? = null,
    val myId: Long = 0,
    val loadError: String? = null,
    val groupMode: Boolean = false,
    val groupName: String = "",
    val selected: Set<Long> = emptySet(),
    /** 채널을 넣을 카테고리(부서) */
    val categories: List<ChatCategory> = emptyList(),
    val categoryId: Long? = null,
    val busy: Boolean = false,
    val errorCode: String? = null,
    /** 열렸거나 만들어진 방 → 화면이 그 방으로 이동 */
    val openedRoomId: Long? = null,
) {
    val teammates: List<TeamMember> get() = team?.members.orEmpty().filter { it.userId != myId }
    /** 그룹 채팅방 만들기 권한 (지금은 팀 OWNER). 서버가 준 권한 목록으로 판단 */
    val canCreateGroup: Boolean get() = team?.team?.canCreateGroupRoom == true
}

class NewChatViewModel(
    private val teamId: Long,
    private val teams: TeamRepository,
    private val chat: ChatRepository,
    private val users: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NewChatUiState())
    val state: StateFlow<NewChatUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            runCatching { users.me().id to teams.detail(teamId) }
                .onSuccess { (myId, team) -> _state.update { it.copy(team = team, myId = myId) } }
                .onFailure { e -> _state.update { it.copy(loadError = e.errorCode) } }
            runCatching { chat.categories(teamId) }.onSuccess { list -> _state.update { it.copy(categories = list) } }
        }
    }

    fun setCategory(id: Long?) = _state.update { it.copy(categoryId = id) }

    fun setGroupMode(group: Boolean) = _state.update { it.copy(groupMode = group) }
    fun setGroupName(name: String) = _state.update { it.copy(groupName = name.take(100)) }
    fun toggle(userId: Long) = _state.update { s -> s.copy(selected = if (userId in s.selected) s.selected - userId else s.selected + userId) }
    fun errorShown() = _state.update { it.copy(errorCode = null) }

    fun openDirect(userId: Long) = run { chat.openDirect(teamId, userId).id }

    fun createGroup() {
        val s = _state.value
        if (s.groupName.isBlank()) return
        run { chat.createGroup(teamId, s.groupName, s.selected.toList(), s.categoryId).id }
    }

    private fun run(block: suspend () -> Long) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { id -> _state.update { it.copy(busy = false, openedRoomId = id) } }
                .onFailure { e -> _state.update { it.copy(busy = false, errorCode = e.errorCode) } }
        }
    }
}

/** 새 대화: 팀원과 1:1, 또는 (팀 소유자만) 그룹 채팅방 만들기 */
@Composable
fun NewChatScreen(
    teamId: Long,
    onBack: () -> Unit,
    onOpened: (roomId: Long) -> Unit,
    viewModel: NewChatViewModel = koinViewModel(key = "new-chat-$teamId") { parametersOf(teamId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    LaunchedEffect(state.openedRoomId) { state.openedRoomId?.let(onOpened) }

    DetailScaffold(title = stringResource(Res.string.chat_new), subtitle = state.team?.team?.name, onBack = onBack) {
        val team = state.team
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                team == null && state.loadError != null -> ErrorState(state.loadError!!, onRetry = viewModel::load)
                team == null -> CircularProgressIndicator(Modifier.padding(48.dp))
                else -> Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    PrimaryTabRow(selectedTabIndex = if (state.groupMode) 1 else 0, containerColor = MaterialTheme.colorScheme.background) {
                        Tab(selected = !state.groupMode, onClick = { viewModel.setGroupMode(false) }, text = { Text(stringResource(Res.string.chat_direct)) })
                        Tab(selected = state.groupMode, onClick = { viewModel.setGroupMode(true) }, text = { Text(stringResource(Res.string.chat_group)) })
                    }
                    when {
                        state.teammates.isEmpty() -> Text(
                            stringResource(Res.string.chat_no_teammates),
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        !state.groupMode -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(state.teammates, key = { it.userId }) { member ->
                                MemberRow(member, enabled = !state.busy, onClick = { viewModel.openDirect(member.userId) })
                            }
                        }
                        !state.canCreateGroup -> Text(
                            stringResource(Res.string.chat_group_owner_only),
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.groupName,
                                onValueChange = viewModel::setGroupName,
                                label = { Text(stringResource(Res.string.chat_group_name)) },
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            CategoryPicker(state.categories, state.categoryId, viewModel::setCategory)
                            LazyColumn(Modifier.weight(1f, fill = false)) {
                                items(state.teammates, key = { it.userId }) { member ->
                                    MemberRow(member, checked = member.userId in state.selected, onClick = { viewModel.toggle(member.userId) })
                                }
                            }
                            PrimaryButton(
                                stringResource(Res.string.chat_group_create),
                                onClick = viewModel::createGroup,
                                enabled = state.groupName.isNotBlank(),
                                loading = state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 팀원 한 줄. checked 를 주면 체크박스로 고르는 모드 */
@Composable
fun MemberRow(member: TeamMember, checked: Boolean? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(member.nickname, resolveImageUrl(member.profileImageUrl), size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(member.nickname, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(member.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (checked != null) Checkbox(checked = checked, onCheckedChange = { onClick() })
    }
}
