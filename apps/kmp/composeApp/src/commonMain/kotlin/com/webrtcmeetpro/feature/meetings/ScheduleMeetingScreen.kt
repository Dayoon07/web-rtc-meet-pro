package com.webrtcmeetpro.feature.meetings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import com.webrtcmeetpro.core.data.MeetingRepository
import com.webrtcmeetpro.core.data.TeamRepository
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.designsystem.component.PrimaryButton
import com.webrtcmeetpro.core.designsystem.component.SecondaryButton
import com.webrtcmeetpro.core.model.ScheduleMeetingRequest
import com.webrtcmeetpro.core.model.TeamDetail
import com.webrtcmeetpro.core.model.TeamSummary
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.feature.chat.MemberRow
import com.webrtcmeetpro.platform.formatDateTime
import com.webrtcmeetpro.platform.nextFullHour
import com.webrtcmeetpro.platform.toOffsetIso
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.common_cancel
import com.webrtcmeetpro.resources.common_ok
import com.webrtcmeetpro.resources.home_no_teams_body
import com.webrtcmeetpro.resources.meeting_description
import com.webrtcmeetpro.resources.meeting_duration
import com.webrtcmeetpro.resources.meeting_invitees
import com.webrtcmeetpro.resources.meeting_length
import com.webrtcmeetpro.resources.meeting_invitees_hint
import com.webrtcmeetpro.resources.meeting_schedule
import com.webrtcmeetpro.resources.meeting_start_now
import com.webrtcmeetpro.resources.meeting_submit
import com.webrtcmeetpro.resources.meeting_team
import com.webrtcmeetpro.resources.meeting_title_label
import com.webrtcmeetpro.resources.meeting_when
import com.webrtcmeetpro.ui.DetailScaffold
import com.webrtcmeetpro.ui.ErrorSnackbarEffect
import com.webrtcmeetpro.ui.ErrorState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class ScheduleUiState(
    val teams: List<TeamSummary>? = null,
    val loadError: String? = null,
    val teamId: Long? = null,
    val team: TeamDetail? = null,
    val myId: Long = 0,
    val title: String = "",
    val description: String = "",
    val startNow: Boolean = false,
    /** DatePicker 기준 UTC 자정 millis */
    val dateMillis: Long = 0,
    val hour: Int = 9,
    val minute: Int = 0,
    val duration: Int = 60,
    val invitees: Set<Long> = emptySet(),
    val submitting: Boolean = false,
    val errorCode: String? = null,
    val createdId: Long? = null,
) {
    val canSubmit get() = teamId != null && title.isNotBlank() && !submitting
    val scheduledIso get() = toOffsetIso(dateMillis, hour, minute)
}

class ScheduleMeetingViewModel(
    private val teams: TeamRepository,
    private val meetings: MeetingRepository,
    private val users: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(nextFullHour().let { (d, h, m) -> ScheduleUiState(dateMillis = d, hour = h, minute = m) })
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()
    private var teamJob: Job? = null

    init { load() }

    fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            runCatching { users.me().id to teams.teams() }
                .onSuccess { (myId, list) ->
                    _state.update { it.copy(teams = list, myId = myId) }
                    list.firstOrNull()?.let { selectTeam(it.id) }
                }
                .onFailure { e -> _state.update { it.copy(loadError = e.errorCode) } }
        }
    }

    fun selectTeam(teamId: Long) {
        _state.update { it.copy(teamId = teamId, team = null, invitees = emptySet()) }
        teamJob?.cancel()
        teamJob = viewModelScope.launch {
            runCatching { teams.detail(teamId) }.onSuccess { t -> _state.update { it.copy(team = t) } }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v.take(100)) }
    fun setDescription(v: String) = _state.update { it.copy(description = v.take(1000)) }
    fun setStartNow(v: Boolean) = _state.update { it.copy(startNow = v) }
    fun setDate(millis: Long) = _state.update { it.copy(dateMillis = millis) }
    fun setTime(hour: Int, minute: Int) = _state.update { it.copy(hour = hour, minute = minute) }
    fun setDuration(v: Int) = _state.update { it.copy(duration = v) }
    fun toggleInvitee(id: Long) = _state.update { s -> s.copy(invitees = if (id in s.invitees) s.invitees - id else s.invitees + id) }
    fun errorShown() = _state.update { it.copy(errorCode = null) }

    fun submit() {
        val s = _state.value
        val teamId = s.teamId ?: return
        if (!s.canSubmit) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val request = ScheduleMeetingRequest(
                title = s.title.trim(),
                description = s.description.trim().ifEmpty { null },
                scheduledAt = if (s.startNow) null else s.scheduledIso,
                durationMinutes = s.duration,
                inviteeIds = s.invitees.toList(),
            )
            runCatching { meetings.schedule(teamId, request) }
                .onSuccess { m -> _state.update { it.copy(submitting = false, createdId = m.id) } }
                .onFailure { e -> _state.update { it.copy(submitting = false, errorCode = e.errorCode) } }
        }
    }
}

private val DURATIONS = listOf(30, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleMeetingScreen(
    onBack: () -> Unit,
    onCreated: (meetingId: Long) -> Unit,
    viewModel: ScheduleMeetingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    LaunchedEffect(state.createdId) { state.createdId?.let(onCreated) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    DetailScaffold(title = stringResource(Res.string.meeting_schedule), onBack = onBack) {
        val teams = state.teams
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            when {
                teams == null && state.loadError != null -> ErrorState(state.loadError!!, onRetry = viewModel::load)
                teams == null -> CircularProgressIndicator(Modifier.padding(48.dp))
                teams.isEmpty() -> Text(stringResource(Res.string.home_no_teams_body), Modifier.padding(24.dp))
                else -> Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Label(stringResource(Res.string.meeting_team))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        teams.forEach { t ->
                            FilterChip(selected = t.id == state.teamId, onClick = { viewModel.selectTeam(t.id) }, label = { Text(t.name) })
                        }
                    }
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::setTitle,
                        label = { Text(stringResource(Res.string.meeting_title_label)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = viewModel::setDescription,
                        label = { Text(stringResource(Res.string.meeting_description)) },
                        minLines = 2,
                        maxLines = 4,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.meeting_start_now), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = state.startNow, onCheckedChange = viewModel::setStartNow)
                    }
                    if (!state.startNow) {
                        Label(stringResource(Res.string.meeting_when))
                        Text(formatDateTime(state.scheduledIso), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(stringResource(Res.string.meeting_when), onClick = { pickDate = true }, icon = Icons.Outlined.CalendarMonth)
                            SecondaryButton(formatTwo(state.hour) + ":" + formatTwo(state.minute), onClick = { pickTime = true }, icon = Icons.Outlined.Schedule)
                        }
                    }

                    Label(stringResource(Res.string.meeting_length))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DURATIONS.forEach { d ->
                            FilterChip(selected = d == state.duration, onClick = { viewModel.setDuration(d) }, label = { Text(stringResource(Res.string.meeting_duration, d)) })
                        }
                    }

                    Label(stringResource(Res.string.meeting_invitees))
                    Text(stringResource(Res.string.meeting_invitees_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val members = state.team?.members.orEmpty().filter { it.userId != state.myId }
                    Column {
                        members.forEach { m -> MemberRow(m, checked = m.userId in state.invitees) { viewModel.toggleInvitee(m.userId) } }
                    }

                    PrimaryButton(
                        stringResource(Res.string.meeting_submit),
                        onClick = viewModel::submit,
                        enabled = state.canSubmit,
                        loading = state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (pickDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = state.dateMillis)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let(viewModel::setDate)
                    pickDate = false
                }) { Text(stringResource(Res.string.common_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(Res.string.common_cancel)) } },
        ) { DatePicker(state = dateState) }
    }
    if (pickTime) {
        val timeState = rememberTimePickerState(initialHour = state.hour, initialMinute = state.minute)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTime(timeState.hour, timeState.minute)
                    pickTime = false
                }) { Text(stringResource(Res.string.common_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}

private fun formatTwo(n: Int) = n.toString().padStart(2, '0')

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}
