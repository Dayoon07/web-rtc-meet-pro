package com.webrtcmeetpro.feature.meetings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.MeetingRepository
import com.webrtcmeetpro.core.data.RealtimeRepository
import com.webrtcmeetpro.core.designsystem.component.EmptyState
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.designsystem.component.SecondaryButton
import com.webrtcmeetpro.core.model.Meeting
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.meeting_schedule
import com.webrtcmeetpro.resources.meetings_empty
import com.webrtcmeetpro.resources.meetings_past
import com.webrtcmeetpro.resources.meetings_title
import com.webrtcmeetpro.resources.meetings_upcoming
import com.webrtcmeetpro.ui.ErrorState
import com.webrtcmeetpro.ui.Loadable
import com.webrtcmeetpro.ui.MeetingCard
import com.webrtcmeetpro.ui.ScreenList
import com.webrtcmeetpro.ui.ScreenTitle
import com.webrtcmeetpro.ui.loadingItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class MeetingsUiState(val past: Boolean = false, val list: Loadable<List<Meeting>> = Loadable())

class MeetingsViewModel(
    private val meetings: MeetingRepository,
    realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MeetingsUiState())
    val state: StateFlow<MeetingsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
        // 예약·시작·취소 알림이 오면 목록도 최신으로
        viewModelScope.launch {
            realtime.notifications.collect { if (it.type.startsWith("MEETING")) refresh(silent = true) }
        }
    }

    fun selectTab(past: Boolean) {
        if (past == _state.value.past) return
        _state.value = MeetingsUiState(past = past)
        refresh()
    }

    fun refresh() = refresh(silent = false)

    private fun refresh(silent: Boolean) {
        val past = _state.value.past
        if (!silent) _state.update { it.copy(list = it.list.copy(loading = true, errorCode = null)) }
        // 탭을 빠르게 바꾸면 이전 탭 응답이 늦게 와서 덮어쓰지 않도록 이전 요청은 취소
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            runCatching { if (past) meetings.past() else meetings.upcoming() }
                .onSuccess { data -> _state.update { it.copy(list = Loadable(data, loading = false)) } }
                .onFailure { e -> _state.update { it.copy(list = it.list.copy(loading = false, errorCode = e.errorCode)) } }
        }
    }
}

@Composable
fun MeetingsScreen(
    onOpen: (Long) -> Unit,
    onSchedule: () -> Unit,
    viewModel: MeetingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val list = state.list.data

    ScreenList(refreshing = state.list.loading && list != null, onRefresh = viewModel::refresh) {
        item {
            ScreenTitle(stringResource(Res.string.meetings_title)) {
                SecondaryButton(stringResource(Res.string.meeting_schedule), onClick = onSchedule, icon = Icons.Outlined.Add)
            }
        }
        item {
            PrimaryTabRow(
                selectedTabIndex = if (state.past) 1 else 0,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                val unselected = MaterialTheme.colorScheme.onSurfaceVariant
                Tab(
                    selected = !state.past,
                    onClick = { viewModel.selectTab(false) },
                    text = { Text(stringResource(Res.string.meetings_upcoming)) },
                    unselectedContentColor = unselected,
                )
                Tab(
                    selected = state.past,
                    onClick = { viewModel.selectTab(true) },
                    text = { Text(stringResource(Res.string.meetings_past)) },
                    unselectedContentColor = unselected,
                )
            }
        }
        when {
            list == null -> state.list.errorCode?.let { code -> item { ErrorState(code, onRetry = viewModel::refresh) } } ?: loadingItem()
            list.isEmpty() -> item {
                LimeetCard(Modifier.fillMaxWidth()) {
                    EmptyState(Icons.Outlined.EventAvailable, stringResource(Res.string.meetings_empty))
                }
            }
            else -> items(list, key = { it.id }) { MeetingCard(it, onClick = { onOpen(it.id) }) }
        }
    }
}
