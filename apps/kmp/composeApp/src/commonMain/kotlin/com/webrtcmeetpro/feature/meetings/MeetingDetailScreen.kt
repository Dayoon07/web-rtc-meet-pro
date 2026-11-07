package com.webrtcmeetpro.feature.meetings

import com.webrtcmeetpro.core.model.MeetingNotes
import kotlinx.coroutines.delay
import com.webrtcmeetpro.resources.meeting_recording_play
import com.webrtcmeetpro.resources.meeting_recordings
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Movie
import org.koin.compose.koinInject
import com.webrtcmeetpro.core.network.ApiConfig
import com.webrtcmeetpro.core.model.Recording
import com.webrtcmeetpro.platform.inAppMeetingSupported
import com.webrtcmeetpro.resources.meeting_edit
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.MeetingRepository
import com.webrtcmeetpro.core.data.RealtimeRepository
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.designsystem.component.Avatar
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.designsystem.component.PrimaryButton
import com.webrtcmeetpro.core.designsystem.component.SecondaryButton
import com.webrtcmeetpro.core.model.Meeting
import com.webrtcmeetpro.core.model.MeetingStatus
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.platform.formatDateTime
import com.webrtcmeetpro.platform.minutesUntil
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.meeting_browser_note
import com.webrtcmeetpro.resources.meeting_cancel
import com.webrtcmeetpro.resources.meeting_cancel_confirm
import com.webrtcmeetpro.resources.meeting_duration
import com.webrtcmeetpro.resources.meeting_host
import com.webrtcmeetpro.resources.meeting_invitees
import com.webrtcmeetpro.resources.meeting_join
import com.webrtcmeetpro.resources.meeting_join_soon
import com.webrtcmeetpro.resources.meeting_keep
import com.webrtcmeetpro.ui.DetailScaffold
import com.webrtcmeetpro.ui.ErrorSnackbarEffect
import com.webrtcmeetpro.ui.ErrorState
import com.webrtcmeetpro.ui.MeetingStatusBadge
import com.webrtcmeetpro.ui.resolveImageUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class MeetingDetailUiState(
    val meeting: Meeting? = null,
    val myId: Long = 0,
    val loadError: String? = null,
    val joining: Boolean = false,
    /** 브라우저로 열 주소. 화면이 한 번 열고 지운다 */
    val joinUrl: String? = null,
    val errorCode: String? = null,
    /** 녹화 (재생 가능한 것만) */
    val recordings: List<Recording> = emptyList(),
    /** AI 회의록 (켠 적이 있을 때만) */
    val notes: MeetingNotes? = null,
    val regenerating: Boolean = false,
) {
    val isHost get() = meeting?.host?.id == myId

    /** 서버 규칙과 같게: 주최자는 언제든, 참석자는 시작 10분 전부터 */
    val canJoin: Boolean
        get() {
            val m = meeting ?: return false
            return m.isOpen && (isHost || m.status == MeetingStatus.LIVE || minutesUntil(m.scheduledAt) <= 10)
        }
}

class MeetingDetailViewModel(
    private val meetingId: Long,
    private val meetings: MeetingRepository,
    private val users: UserRepository,
    realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MeetingDetailUiState())
    val state: StateFlow<MeetingDetailUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            realtime.notifications.collect { if (it.link?.endsWith("/meetings/$meetingId") == true) load() }
        }
    }

    fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            runCatching { users.me().id to meetings.detail(meetingId) }
                .onSuccess { (myId, m) -> _state.update { it.copy(meeting = m, myId = myId) } }
                .onFailure { e -> _state.update { it.copy(loadError = e.errorCode) } }
            runCatching { meetings.recordings(meetingId) }
                .onSuccess { list -> _state.update { it.copy(recordings = list.filter { r -> r.url != null }) } }
            loadNotes()
        }
    }

    private suspend fun loadNotes() {
        if (_state.value.meeting?.notesStatus == null) return
        runCatching { meetings.notes(meetingId) }.onSuccess { n -> _state.update { it.copy(notes = n) } }
        // 요약 중이면 끝날 때까지 가끔 다시
        while (_state.value.notes?.status in setOf("RECORDING", "PAUSED", "PROCESSING")) {
            delay(5_000)
            runCatching { meetings.notes(meetingId) }.onSuccess { n -> _state.update { it.copy(notes = n) } }
        }
    }

    fun regenerateNotes() {
        if (_state.value.regenerating) return
        _state.update { it.copy(regenerating = true) }
        viewModelScope.launch {
            runCatching { meetings.regenerateNotes(meetingId) }
                .onSuccess { n -> _state.update { it.copy(regenerating = false, notes = n) }; loadNotes() }
                .onFailure { e -> _state.update { it.copy(regenerating = false, errorCode = e.errorCode) } }
        }
    }

    fun join() {
        if (_state.value.joining) return
        _state.update { it.copy(joining = true) }
        viewModelScope.launch {
            runCatching { meetings.browserJoinUrl(meetingId) }
                .onSuccess { url -> _state.update { it.copy(joining = false, joinUrl = url) } }
                .onFailure { e -> _state.update { it.copy(joining = false, errorCode = e.errorCode) } }
        }
    }

    fun joinOpened() = _state.update { it.copy(joinUrl = null) }

    fun cancel() {
        viewModelScope.launch {
            runCatching { meetings.cancel(meetingId); meetings.detail(meetingId) }
                .onSuccess { m -> _state.update { it.copy(meeting = m) } }
                .onFailure { e -> _state.update { it.copy(errorCode = e.errorCode) } }
        }
    }

    fun errorShown() = _state.update { it.copy(errorCode = null) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetingDetailScreen(
    meetingId: Long,
    onBack: () -> Unit,
    /** 예약 회의 고치기 화면 열기 */
    onEdit: (meetingId: Long) -> Unit = {},
    /** Android: 앱 안 회의 화면으로 (Windows 는 브라우저) */
    onJoinInApp: (url: String) -> Unit = {},
    viewModel: MeetingDetailViewModel = koinViewModel(key = "meeting-$meetingId") { parametersOf(meetingId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    ErrorSnackbarEffect(state.errorCode, viewModel::errorShown)
    LaunchedEffect(state.joinUrl) {
        state.joinUrl?.let {
            viewModel.joinOpened()
            if (inAppMeetingSupported) onJoinInApp(it) else uriHandler.openUri(it)
        }
    }
    var confirmCancel by remember { mutableStateOf(false) }
    val meeting = state.meeting

    DetailScaffold(title = meeting?.title.orEmpty(), subtitle = meeting?.teamName, onBack = onBack) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            when {
                meeting == null && state.loadError != null -> ErrorState(state.loadError!!, onRetry = viewModel::load)
                meeting == null -> CircularProgressIndicator(Modifier.padding(48.dp))
                else -> Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LimeetCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(meeting.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                                MeetingStatusBadge(meeting.status)
                            }
                            Text(
                                "${formatDateTime(meeting.scheduledAt)} · " + stringResource(Res.string.meeting_duration, meeting.durationMinutes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            meeting.host?.let { host ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Avatar(host.nickname, resolveImageUrl(host.profileImageUrl), size = 24.dp)
                                    Text(stringResource(Res.string.meeting_host, host.nickname), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            meeting.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (meeting.isOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PrimaryButton(
                                text = stringResource(if (state.canJoin) Res.string.meeting_join else Res.string.meeting_join_soon),
                                onClick = viewModel::join,
                                enabled = state.canJoin,
                                loading = state.joining,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // Android 는 앱 안에서, Windows 는 브라우저로 열린다
                            if (!inAppMeetingSupported) {
                                Text(
                                    stringResource(Res.string.meeting_browser_note),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    state.notes?.let { MeetingNotesSection(it, state.regenerating, viewModel::regenerateNotes) }

                    if (state.recordings.isNotEmpty()) {
                        Text(stringResource(Res.string.meeting_recordings), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        val config = koinInject<ApiConfig>()
                        state.recordings.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(formatDateTime(r.startedAt), style = MaterialTheme.typography.bodyMedium)
                                    val minutes = (r.durationSeconds ?: 0) / 60
                                    Text(
                                        "${r.startedBy?.nickname ?: ""} · ${stringResource(Res.string.meeting_duration, minutes)} · ${r.sizeBytes / 1024 / 1024} MB",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // 재생은 브라우저(또는 동영상 앱)에서
                                TextButton(onClick = { config.resolve(r.url)?.let { runCatching { uriHandler.openUri(it) } } }) {
                                    Text(stringResource(Res.string.meeting_recording_play))
                                }
                            }
                        }
                    }

                    if (meeting.invitees.isNotEmpty()) {
                        Text(stringResource(Res.string.meeting_invitees), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            meeting.invitees.forEach { u ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Avatar(u.nickname, resolveImageUrl(u.profileImageUrl), size = 28.dp)
                                    Text(u.nickname, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // 주최자이거나 팀 관리 권한이 있으면 (서버가 계산한 canEdit / canCancel)
                    if (meeting.canEdit) {
                        SecondaryButton(stringResource(Res.string.meeting_edit), onClick = { onEdit(meeting.id) }, icon = Icons.Outlined.Edit)
                    }
                    if (meeting.canCancel) {
                        SecondaryButton(stringResource(Res.string.meeting_cancel), onClick = { confirmCancel = true }, icon = Icons.Outlined.EventBusy)
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            text = { Text(stringResource(Res.string.meeting_cancel_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; viewModel.cancel() }) {
                    Text(stringResource(Res.string.meeting_cancel), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(Res.string.meeting_keep)) } },
        )
    }
}
