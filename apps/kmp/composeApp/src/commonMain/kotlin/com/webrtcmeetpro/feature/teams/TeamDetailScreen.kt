package com.webrtcmeetpro.feature.teams

import com.webrtcmeetpro.resources.team_role_changed
import com.webrtcmeetpro.resources.team_role_change
import com.webrtcmeetpro.core.model.TeamMember
import com.webrtcmeetpro.core.data.UserRepository
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.TeamRepository
import com.webrtcmeetpro.core.designsystem.component.Avatar
import com.webrtcmeetpro.core.designsystem.component.Badge
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.model.Invitation
import com.webrtcmeetpro.core.model.InvitationStatus
import com.webrtcmeetpro.core.model.TeamDetail
import com.webrtcmeetpro.core.model.TeamRole
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.platform.formatDateTime
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.invitation_expires
import com.webrtcmeetpro.resources.role_ADMIN
import com.webrtcmeetpro.resources.role_MEMBER
import com.webrtcmeetpro.resources.role_OWNER
import com.webrtcmeetpro.resources.team_invite
import com.webrtcmeetpro.resources.team_invite_email
import com.webrtcmeetpro.resources.team_invite_sent
import com.webrtcmeetpro.resources.team_members_limit
import com.webrtcmeetpro.resources.team_pending
import com.webrtcmeetpro.resources.teams_title
import com.webrtcmeetpro.ui.DetailScaffold
import com.webrtcmeetpro.ui.ErrorSnackbarEffect
import com.webrtcmeetpro.ui.ErrorState
import com.webrtcmeetpro.ui.LocalSnackbar
import com.webrtcmeetpro.ui.resolveImageUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonRemove
import com.webrtcmeetpro.resources.common_cancel
import com.webrtcmeetpro.resources.team_delete
import com.webrtcmeetpro.resources.team_delete_body
import com.webrtcmeetpro.resources.team_delete_confirm_label
import com.webrtcmeetpro.resources.team_leave
import com.webrtcmeetpro.resources.team_leave_confirm
import com.webrtcmeetpro.resources.team_remove
import com.webrtcmeetpro.resources.team_remove_confirm
import com.webrtcmeetpro.resources.team_removed

data class TeamDetailUiState(
    val team: TeamDetail? = null,
    val myId: Long = 0,
    /** 역할을 바꾼 팀원 (스낵바 한 번) */
    val roleChanged: TeamMember? = null,
    /** 내보낸 팀원 (스낵바 한 번) */
    val removed: TeamMember? = null,
    /** 팀을 나갔거나 삭제했다 → 화면을 닫는다 */
    val exited: Boolean = false,
    val busy: Boolean = false,
    val pending: List<Invitation> = emptyList(),
    val loadError: String? = null,
    val email: String = "",
    val inviting: Boolean = false,
    val invitedTo: String? = null,
    val errorCode: String? = null,
)

class TeamDetailViewModel(
    private val teamId: Long,
    private val teams: TeamRepository,
    private val users: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeamDetailUiState())
    val state: StateFlow<TeamDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            runCatching {
                val detail = teams.detail(teamId)
                val pending = if (detail.team.canInvite) {
                    teams.pendingInvitations(teamId).filter { it.status == InvitationStatus.PENDING }
                } else emptyList()
                Triple(detail, pending, users.me().id)
            }.onSuccess { (detail, pending, myId) -> _state.update { it.copy(team = detail, pending = pending, myId = myId) } }
                .onFailure { e -> _state.update { it.copy(loadError = e.errorCode) } }
        }
    }

    fun setEmail(v: String) = _state.update { it.copy(email = v.trim().take(255)) }

    fun invite() {
        val email = _state.value.email
        if (!email.contains('@') || _state.value.inviting) return
        _state.update { it.copy(inviting = true) }
        viewModelScope.launch {
            runCatching { teams.invite(teamId, email) }
                .onSuccess { inv -> _state.update { it.copy(inviting = false, email = "", invitedTo = email, pending = listOf(inv) + it.pending) } }
                .onFailure { e -> _state.update { it.copy(inviting = false, errorCode = e.errorCode) } }
        }
    }

    fun cancelInvitation(inv: Invitation) {
        viewModelScope.launch {
            runCatching { teams.cancelInvitation(teamId, inv.id) }
                .onSuccess { _state.update { s -> s.copy(pending = s.pending.filterNot { it.id == inv.id }) } }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    /** ADMIN 지정·해제 (팀 OWNER 만. 서버가 다시 확인한다) */
    fun changeRole(member: TeamMember, role: TeamRole) {
        if (member.role == role) return
        viewModelScope.launch {
            runCatching { teams.changeRole(teamId, member.userId, role) }
                .onSuccess { updated ->
                    _state.update { s ->
                        s.copy(
                            team = s.team?.copy(members = s.team.members.map { if (it.userId == updated.userId) updated else it }),
                            roleChanged = updated,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun roleChangedShown() = _state.update { it.copy(roleChanged = null) }

    /** 팀 나가기. OWNER 는 서버가 막는다 (팀 삭제만 할 수 있다) */
    fun leave() = exit { teams.leave(teamId) }

    /** 팀 삭제 (DELETE_TEAM 권한. 서버가 다시 확인한다) */
    fun deleteTeam() = exit { teams.delete(teamId) }

    private fun exit(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { _state.update { it.copy(busy = false, exited = true) } }
                .onFailure { e -> _state.update { it.copy(busy = false, errorCode = e.errorCode) } }
        }
    }

    /** 팀원 내보내기 (서버가 removable 로 알려 준 팀원만 버튼이 보인다) */
    fun removeMember(member: TeamMember) {
        viewModelScope.launch {
            runCatching { teams.removeMember(teamId, member.userId) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(team = s.team?.copy(members = s.team.members.filterNot { it.userId == member.userId }), removed = member)
                    }
                }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun removedShown() = _state.update { it.copy(removed = null) }

    fun invitedShown() = _state.update { it.copy(invitedTo = null) }
    fun errorShown() = _state.update { it.copy(errorCode = null) }
}

@Composable
fun TeamDetailScreen(
    teamId: Long,
    onBack: () -> Unit,
    viewModel: TeamDetailViewModel = koinViewModel(key = "team-$teamId") { parametersOf(teamId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    LaunchedEffect(state.invitedTo) {
        state.invitedTo?.let {
            viewModel.invitedShown()
            snackbar.showSnackbar(getString(Res.string.team_invite_sent, it))
        }
    }
    LaunchedEffect(state.roleChanged) {
        state.roleChanged?.let {
            viewModel.roleChangedShown()
            snackbar.showSnackbar(getString(Res.string.team_role_changed, it.nickname, getString(roleLabel(it.role))))
        }
    }
    LaunchedEffect(state.removed) {
        state.removed?.let {
            viewModel.removedShown()
            snackbar.showSnackbar(getString(Res.string.team_removed, it.nickname))
        }
    }
    LaunchedEffect(state.exited) { if (state.exited) onBack() }
    val detail = state.team
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<TeamMember?>(null) }

    DetailScaffold(
        title = detail?.team?.name ?: stringResource(Res.string.teams_title),
        subtitle = detail?.team?.description,
        onBack = onBack,
        actions = {
            // 삭제 권한(OWNER)이 있으면 "팀 삭제", 아니면 "팀 나가기"
            if (detail != null) {
                if (detail.team.canDeleteTeam) {
                    IconButton(onClick = { confirmDelete = true }, enabled = !state.busy) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.team_delete))
                    }
                } else {
                    IconButton(onClick = { confirmLeave = true }, enabled = !state.busy) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = stringResource(Res.string.team_leave))
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            when {
                detail == null && state.loadError != null -> ErrorState(state.loadError!!, onRetry = viewModel::load)
                detail == null -> CircularProgressIndicator(Modifier.padding(48.dp))
                else -> Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(Res.string.team_members_limit, detail.limits.usedMembers.takeIf { it > 0 } ?: detail.members.size, detail.limits.maxMembers),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (detail.team.canInvite) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.email,
                                onValueChange = viewModel::setEmail,
                                label = { Text(stringResource(Res.string.team_invite_email)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = viewModel::invite, enabled = state.email.contains('@') && !state.inviting) {
                                Text(stringResource(Res.string.team_invite))
                            }
                        }
                        if (state.pending.isNotEmpty()) {
                            Text(stringResource(Res.string.team_pending), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            LimeetCard(Modifier.fillMaxWidth()) {
                                Column {
                                    state.pending.forEachIndexed { i, inv ->
                                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(inv.inviteeEmail, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    stringResource(Res.string.invitation_expires, formatDateTime(inv.expiresAt)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            IconButton(onClick = { viewModel.cancelInvitation(inv) }) {
                                                Icon(Icons.Outlined.Close, contentDescription = null)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LimeetCard(Modifier.fillMaxWidth()) {
                        Column {
                            detail.members.forEachIndexed { i, m ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Avatar(m.nickname, resolveImageUrl(m.profileImageUrl), size = 40.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(m.nickname, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                        Text(m.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    // 팀 OWNER 는 다른 팀원을 ADMIN 으로 지정·해제할 수 있다 (OWNER 와 나 자신은 제외)
                                    if (detail.team.canManageRoles && m.role != TeamRole.OWNER && m.userId != state.myId) {
                                        RoleMenu(m, onChange = { viewModel.changeRole(m, it) })
                                    } else {
                                        Badge(stringResource(roleLabel(m.role)))
                                    }
                                    if (m.removable) {
                                        IconButton(onClick = { confirmRemove = m }) {
                                            Icon(Icons.Outlined.PersonRemove, contentDescription = stringResource(Res.string.team_remove))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmLeave && detail != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            text = { Text(stringResource(Res.string.team_leave_confirm, detail.team.name)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; viewModel.leave() }) {
                    Text(stringResource(Res.string.team_leave), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
    if (confirmDelete && detail != null) {
        DeleteTeamDialog(detail.team.name, onDismiss = { confirmDelete = false }, onConfirm = { confirmDelete = false; viewModel.deleteTeam() })
    }
    confirmRemove?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            text = { Text(stringResource(Res.string.team_remove_confirm, m.nickname)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = null; viewModel.removeMember(m) }) {
                    Text(stringResource(Res.string.team_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}

/** 팀 삭제는 되돌릴 수 없어서 팀 이름을 그대로 입력해야 지울 수 있다 */
@Composable
private fun DeleteTeamDialog(teamName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.team_delete)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.team_delete_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(100) },
                    label = { Text(stringResource(Res.string.team_delete_confirm_label)) },
                    placeholder = { Text(teamName) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == teamName) {
                Text(stringResource(Res.string.team_delete), color = if (typed.trim() == teamName) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}


private fun roleLabel(role: TeamRole) = when (role) {
    TeamRole.OWNER -> Res.string.role_OWNER
    TeamRole.ADMIN -> Res.string.role_ADMIN
    TeamRole.MEMBER -> Res.string.role_MEMBER
}

/** 현재 역할 버튼 → 관리자 / 팀원 고르기 */
@Composable
private fun RoleMenu(member: TeamMember, onChange: (TeamRole) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(roleLabel(member.role)))
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = stringResource(Res.string.team_role_change))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(TeamRole.ADMIN, TeamRole.MEMBER).forEach { role ->
                DropdownMenuItem(
                    text = { Text(stringResource(roleLabel(role))) },
                    onClick = {
                        open = false
                        onChange(role)
                    },
                )
            }
        }
    }
}
