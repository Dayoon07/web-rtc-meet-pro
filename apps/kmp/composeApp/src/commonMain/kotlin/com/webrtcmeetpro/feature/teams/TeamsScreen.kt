package com.webrtcmeetpro.feature.teams

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.BadgeRepository
import com.webrtcmeetpro.core.data.RealtimeRepository
import com.webrtcmeetpro.core.data.SettingsStore
import com.webrtcmeetpro.core.data.TeamRepository
import com.webrtcmeetpro.core.designsystem.component.EmptyState
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.designsystem.component.PrimaryButton
import com.webrtcmeetpro.core.designsystem.component.SectionHeader
import com.webrtcmeetpro.core.model.Invitation
import com.webrtcmeetpro.core.model.InvitationStatus
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.invitation_accept
import com.webrtcmeetpro.resources.invitation_decline
import com.webrtcmeetpro.resources.invitation_from
import com.webrtcmeetpro.resources.invitations_empty
import com.webrtcmeetpro.resources.invitations_title
import com.webrtcmeetpro.resources.start_create_title
import com.webrtcmeetpro.resources.start_invites_hint
import com.webrtcmeetpro.resources.start_invites_title
import com.webrtcmeetpro.resources.start_subtitle
import com.webrtcmeetpro.resources.start_title
import com.webrtcmeetpro.resources.team_create
import com.webrtcmeetpro.resources.team_description
import com.webrtcmeetpro.resources.team_name
import com.webrtcmeetpro.ui.ErrorSnackbarEffect
import com.webrtcmeetpro.ui.ScreenList
import com.webrtcmeetpro.ui.ScreenTitle
import com.webrtcmeetpro.ui.loadingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class TeamsUiState(
    /** 받은 초대 (대기 중인 것만). null = 아직 불러오는 중 */
    val invitations: List<Invitation>? = null,
    val loading: Boolean = false,
    val creating: Boolean = false,
    /** 팀을 만들었거나 초대를 수락해 들어간 팀 → 그 팀 채팅으로 */
    val joinedTeamId: Long? = null,
    val errorCode: String? = null,
)

/**
 * 받은 초대와 팀 만들기. 팀은 한 사람당 하나 (회사 하나 = 팀 하나, 그 안에 채팅방은 몇 개든):
 * 팀이 없으면 처음에 팀을 만들거나 받은 초대를 수락한다. 들어간 팀은 "마지막에 본 팀" 으로 기억해 채팅이 그 팀으로 열린다
 */
class TeamsViewModel(
    private val teams: TeamRepository,
    private val badges: BadgeRepository,
    private val settings: SettingsStore,
    realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeamsUiState())
    val state: StateFlow<TeamsUiState> = _state.asStateFlow()

    init {
        refresh()
        // 초대가 오면 다시 불러온다
        viewModelScope.launch {
            realtime.notifications.collect { if (it.type.startsWith("TEAM")) refresh() }
        }
    }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching { teams.myInvitations().filter { it.status == InvitationStatus.PENDING } }
                .onSuccess { list -> _state.update { it.copy(invitations = list, loading = false) } }
                .onFailure { e -> _state.update { it.copy(invitations = it.invitations ?: emptyList(), loading = false, errorCode = e.errorCode) } }
        }
    }

    fun respond(invitation: Invitation, accept: Boolean) {
        viewModelScope.launch {
            runCatching { if (accept) teams.accept(invitation.id) else teams.decline(invitation.id) }
                .onSuccess {
                    refresh()
                    badges.refresh()
                    if (accept) joined(invitation.teamId)
                }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun create(name: String, description: String) {
        if (name.isBlank() || _state.value.creating) return
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            runCatching { teams.create(name, description) }
                .onSuccess { team -> _state.update { it.copy(creating = false) }; joined(team.id) }
                .onFailure { e -> _state.update { it.copy(creating = false, errorCode = e.errorCode) } }
        }
    }

    private suspend fun joined(teamId: Long) {
        settings.setLastTeam(teamId)
        _state.update { it.copy(joinedTeamId = teamId) }
    }

    fun joinedHandled() = _state.update { it.copy(joinedTeamId = null) }
    fun errorShown() = _state.update { it.copy(errorCode = null) }
}

/** "받은 초대" 탭: 수락하면 그 팀 채팅으로 */
@Composable
fun InvitationsScreen(onJoined: (teamId: Long) -> Unit, viewModel: TeamsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    LaunchedEffect(state.joinedTeamId) {
        state.joinedTeamId?.let {
            viewModel.joinedHandled()
            onJoined(it)
        }
    }
    val invitations = state.invitations
    ScreenList(refreshing = state.loading && invitations != null, onRefresh = viewModel::refresh) {
        item { ScreenTitle(stringResource(Res.string.invitations_title)) }
        when {
            invitations == null -> loadingItem()
            invitations.isEmpty() -> item {
                LimeetCard(Modifier.fillMaxWidth()) {
                    EmptyState(Icons.Outlined.MailOutline, stringResource(Res.string.invitations_empty))
                }
            }
            else -> items(invitations, key = { it.id }) { inv ->
                InvitationCard(inv, onAccept = { viewModel.respond(inv, true) }, onDecline = { viewModel.respond(inv, false) })
            }
        }
    }
}

/**
 * 처음 시작 화면 (팀이 하나도 없을 때): 받은 초대를 수락하거나, 내 팀을 만든다.
 * 수락하면 따로 팀을 만들 필요 없다. 팀이 생기면 onReady → 메인 화면
 */
@Composable
fun TeamStartScreen(onReady: () -> Unit, viewModel: TeamsViewModel = koinViewModel(key = "team-start")) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    LaunchedEffect(state.joinedTeamId) {
        state.joinedTeamId?.let {
            viewModel.joinedHandled()
            onReady()
        }
    }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    ScreenList(refreshing = false, onRefresh = viewModel::refresh) {
        item { ScreenTitle(stringResource(Res.string.start_title), subtitle = stringResource(Res.string.start_subtitle)) }
        val invitations = state.invitations.orEmpty()
        if (invitations.isNotEmpty()) {
            item { SectionHeader(stringResource(Res.string.start_invites_title)) }
            item {
                Text(
                    stringResource(Res.string.start_invites_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(invitations, key = { "inv${it.id}" }) { inv ->
                InvitationCard(inv, onAccept = { viewModel.respond(inv, true) }, onDecline = { viewModel.respond(inv, false) })
            }
        }
        item { SectionHeader(stringResource(Res.string.start_create_title)) }
        item {
            LimeetCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(100) },
                        label = { Text(stringResource(Res.string.team_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(500) },
                        label = { Text(stringResource(Res.string.team_description)) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        stringResource(Res.string.team_create),
                        onClick = { viewModel.create(name, description) },
                        enabled = name.isNotBlank(),
                        loading = state.creating,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun InvitationCard(inv: Invitation, onAccept: () -> Unit, onDecline: () -> Unit) {
    LimeetCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(inv.teamName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(Res.string.invitation_from, inv.inviterName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onDecline) { Text(stringResource(Res.string.invitation_decline)) }
            Button(onClick = onAccept) { Text(stringResource(Res.string.invitation_accept)) }
        }
    }
}
