package com.webrtcmeetpro.feature.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.BadgeRepository
import com.webrtcmeetpro.core.data.RealtimeRepository
import com.webrtcmeetpro.core.data.TeamRepository
import com.webrtcmeetpro.core.designsystem.component.EmptyState
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.designsystem.component.SecondaryButton
import com.webrtcmeetpro.core.designsystem.component.SectionHeader
import com.webrtcmeetpro.core.model.Invitation
import com.webrtcmeetpro.core.model.InvitationStatus
import com.webrtcmeetpro.core.model.TeamSummary
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.common_cancel
import com.webrtcmeetpro.resources.home_no_teams
import com.webrtcmeetpro.resources.home_no_teams_body
import com.webrtcmeetpro.resources.invitation_accept
import com.webrtcmeetpro.resources.invitation_decline
import com.webrtcmeetpro.resources.invitation_from
import com.webrtcmeetpro.resources.invitations_title
import com.webrtcmeetpro.resources.team_create
import com.webrtcmeetpro.resources.team_description
import com.webrtcmeetpro.resources.team_name
import com.webrtcmeetpro.resources.teams_title
import com.webrtcmeetpro.ui.ErrorSnackbarEffect
import com.webrtcmeetpro.ui.ErrorState
import com.webrtcmeetpro.ui.Loadable
import com.webrtcmeetpro.ui.ScreenList
import com.webrtcmeetpro.ui.ScreenTitle
import com.webrtcmeetpro.ui.TeamCard
import com.webrtcmeetpro.ui.loadingItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class TeamsUiState(
    val teams: Loadable<List<TeamSummary>> = Loadable(),
    val invitations: List<Invitation> = emptyList(),
    val creating: Boolean = false,
    val createdTeamId: Long? = null,
    val errorCode: String? = null,
)

class TeamsViewModel(
    private val teams: TeamRepository,
    private val badges: BadgeRepository,
    realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeamsUiState())
    val state: StateFlow<TeamsUiState> = _state.asStateFlow()

    init {
        refresh()
        // 초대가 오거나 누가 팀에 들어오면 다시 불러온다
        viewModelScope.launch {
            realtime.notifications.collect { if (it.type.startsWith("TEAM")) refresh() }
        }
    }

    fun refresh() {
        _state.update { it.copy(teams = it.teams.copy(loading = true, errorCode = null)) }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val list = async { teams.teams() }
                    val invites = async { runCatching { teams.myInvitations() }.getOrDefault(emptyList()) }
                    list.await() to invites.await().filter { it.status == InvitationStatus.PENDING }
                }
            }.onSuccess { (list, invites) -> _state.update { it.copy(teams = Loadable(list, loading = false), invitations = invites) } }
                .onFailure { e -> _state.update { it.copy(teams = it.teams.copy(loading = false, errorCode = e.errorCode)) } }
        }
    }

    fun respond(invitation: Invitation, accept: Boolean) {
        viewModelScope.launch {
            runCatching { if (accept) teams.accept(invitation.id) else teams.decline(invitation.id) }
                .onSuccess { refresh(); badges.refresh() }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun create(name: String, description: String) {
        if (name.isBlank() || _state.value.creating) return
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            runCatching { teams.create(name, description) }
                .onSuccess { team -> _state.update { it.copy(creating = false, createdTeamId = team.id) }; refresh() }
                .onFailure { e -> _state.update { it.copy(creating = false, errorCode = e.errorCode) } }
        }
    }

    fun createdHandled() = _state.update { it.copy(createdTeamId = null) }
    fun errorShown() = _state.update { it.copy(errorCode = null) }
}

@Composable
fun TeamsScreen(onOpen: (Long) -> Unit, viewModel: TeamsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val teams = state.teams.data
    var showCreate by remember { mutableStateOf(false) }
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    LaunchedEffect(state.createdTeamId) {
        state.createdTeamId?.let {
            showCreate = false
            viewModel.createdHandled()
            onOpen(it)
        }
    }

    ScreenList(refreshing = state.teams.loading && teams != null, onRefresh = viewModel::refresh) {
        item {
            ScreenTitle(stringResource(Res.string.teams_title)) {
                SecondaryButton(stringResource(Res.string.team_create), onClick = { showCreate = true }, icon = Icons.Outlined.Add)
            }
        }
        if (state.invitations.isNotEmpty()) {
            item { SectionHeader(stringResource(Res.string.invitations_title)) }
            items(state.invitations, key = { "inv${it.id}" }) { inv ->
                InvitationCard(inv, onAccept = { viewModel.respond(inv, true) }, onDecline = { viewModel.respond(inv, false) })
            }
        }
        when {
            teams == null -> state.teams.errorCode?.let { code -> item { ErrorState(code, onRetry = viewModel::refresh) } } ?: loadingItem()
            teams.isEmpty() -> item {
                LimeetCard(Modifier.fillMaxWidth()) {
                    EmptyState(Icons.Outlined.GroupAdd, stringResource(Res.string.home_no_teams), body = stringResource(Res.string.home_no_teams_body))
                }
            }
            else -> items(teams, key = { it.id }) { TeamCard(it, onClick = { onOpen(it.id) }) }
        }
    }

    if (showCreate) {
        CreateTeamDialog(busy = state.creating, onDismiss = { showCreate = false }, onCreate = viewModel::create)
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

@Composable
private fun CreateTeamDialog(busy: Boolean, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.team_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, description) }, enabled = name.isNotBlank() && !busy) {
                Text(stringResource(Res.string.team_create), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}
