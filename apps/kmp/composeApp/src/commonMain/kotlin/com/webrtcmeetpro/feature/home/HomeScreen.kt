package com.webrtcmeetpro.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.webrtcmeetpro.core.designsystem.component.Avatar
import com.webrtcmeetpro.core.designsystem.component.Badge
import com.webrtcmeetpro.core.designsystem.component.EmptyState
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.designsystem.component.SectionHeader
import com.webrtcmeetpro.core.model.Me
import com.webrtcmeetpro.core.model.Meeting
import com.webrtcmeetpro.core.model.TeamSummary
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.common_see_all
import com.webrtcmeetpro.resources.home_greeting
import com.webrtcmeetpro.resources.home_my_teams
import com.webrtcmeetpro.resources.home_no_meetings
import com.webrtcmeetpro.resources.home_no_meetings_body
import com.webrtcmeetpro.resources.home_no_teams
import com.webrtcmeetpro.resources.home_no_teams_body
import com.webrtcmeetpro.resources.home_subtitle
import com.webrtcmeetpro.resources.home_upcoming
import com.webrtcmeetpro.ui.ErrorState
import com.webrtcmeetpro.ui.Loadable
import com.webrtcmeetpro.ui.MeetingCard
import com.webrtcmeetpro.ui.ScreenList
import com.webrtcmeetpro.ui.loadingItem
import com.webrtcmeetpro.ui.TeamCard
import com.webrtcmeetpro.ui.resolveImageUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class HomeData(val me: Me, val upcoming: List<Meeting>, val teams: List<TeamSummary>)

class HomeViewModel(
    private val users: UserRepository,
    private val meetings: MeetingRepository,
    private val teams: TeamRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<HomeData>())
    val state: StateFlow<Loadable<HomeData>> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, errorCode = null) }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val me = async { users.me() }
                    val upcoming = async { meetings.upcoming() }
                    val myTeams = async { teams.teams() }
                    HomeData(me.await(), upcoming.await(), myTeams.await())
                }
            }.onSuccess { data -> _state.value = Loadable(data, loading = false) }
                .onFailure { e -> _state.update { it.copy(loading = false, errorCode = e.errorCode) } }
        }
    }
}

@Composable
fun HomeScreen(
    onOpenMeetings: () -> Unit,
    onOpenTeams: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data = state.data

    ScreenList(refreshing = state.loading && data != null, onRefresh = viewModel::refresh) {
        if (data == null) {
            val code = state.errorCode
            if (code != null) item { ErrorState(code, onRetry = viewModel::refresh) } else loadingItem()
            return@ScreenList
        }
        item { Greeting(data.me) }

        item {
            SectionHeader(stringResource(Res.string.home_upcoming), Modifier.padding(top = 12.dp)) {
                TextButton(onClick = onOpenMeetings) { Text(stringResource(Res.string.common_see_all)) }
            }
        }
        if (data.upcoming.isEmpty()) {
            item {
                LimeetCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        Icons.Outlined.EventAvailable,
                        stringResource(Res.string.home_no_meetings),
                        body = stringResource(Res.string.home_no_meetings_body),
                    )
                }
            }
        } else {
            items(data.upcoming.take(3), key = { "m${it.id}" }) { MeetingCard(it) }
        }

        item {
            SectionHeader(stringResource(Res.string.home_my_teams), Modifier.padding(top = 12.dp)) {
                TextButton(onClick = onOpenTeams) { Text(stringResource(Res.string.common_see_all)) }
            }
        }
        if (data.teams.isEmpty()) {
            item {
                LimeetCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        Icons.Outlined.GroupAdd,
                        stringResource(Res.string.home_no_teams),
                        body = stringResource(Res.string.home_no_teams_body),
                    )
                }
            }
        } else {
            items(data.teams.take(4), key = { "t${it.id}" }) { TeamCard(it) }
        }
    }
}

@Composable
private fun Greeting(me: Me) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Avatar(me.nickname, resolveImageUrl(me.profileImageUrl), size = 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.home_greeting, me.nickname),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Badge(me.plan.name)
            }
            Text(
                stringResource(Res.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
