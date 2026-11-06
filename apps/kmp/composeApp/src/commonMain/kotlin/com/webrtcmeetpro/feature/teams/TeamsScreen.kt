package com.webrtcmeetpro.feature.teams

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.TeamRepository
import com.webrtcmeetpro.core.designsystem.component.EmptyState
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.model.TeamSummary
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.home_no_teams
import com.webrtcmeetpro.resources.home_no_teams_body
import com.webrtcmeetpro.resources.teams_title
import com.webrtcmeetpro.ui.ErrorState
import com.webrtcmeetpro.ui.Loadable
import com.webrtcmeetpro.ui.ScreenList
import com.webrtcmeetpro.ui.ScreenTitle
import com.webrtcmeetpro.ui.TeamCard
import com.webrtcmeetpro.ui.loadingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class TeamsViewModel(private val teams: TeamRepository) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<List<TeamSummary>>())
    val state: StateFlow<Loadable<List<TeamSummary>>> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, errorCode = null) }
        viewModelScope.launch {
            runCatching { teams.teams() }
                .onSuccess { _state.value = Loadable(it, loading = false) }
                .onFailure { e -> _state.update { it.copy(loading = false, errorCode = e.errorCode) } }
        }
    }
}

@Composable
fun TeamsScreen(viewModel: TeamsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val teams = state.data

    ScreenList(refreshing = state.loading && teams != null, onRefresh = viewModel::refresh) {
        item { ScreenTitle(stringResource(Res.string.teams_title)) }
        when {
            teams == null -> state.errorCode?.let { code -> item { ErrorState(code, onRetry = viewModel::refresh) } } ?: loadingItem()
            teams.isEmpty() -> item {
                LimeetCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        Icons.Outlined.GroupAdd,
                        stringResource(Res.string.home_no_teams),
                        body = stringResource(Res.string.home_no_teams_body),
                    )
                }
            }
            else -> items(teams, key = { it.id }) { TeamCard(it) }
        }
    }
}
