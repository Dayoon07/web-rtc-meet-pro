package com.webrtcmeetpro.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.webrtcmeetpro.core.data.BadgeRepository
import com.webrtcmeetpro.core.data.NotificationRepository
import com.webrtcmeetpro.core.data.RealtimeRepository
import com.webrtcmeetpro.core.designsystem.component.EmptyState
import com.webrtcmeetpro.core.model.AppNotification
import com.webrtcmeetpro.core.network.errorCode
import com.webrtcmeetpro.platform.formatDateTime
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.notif_CHAT_ROOM_INVITED
import com.webrtcmeetpro.resources.notif_MEETING_CANCELED
import com.webrtcmeetpro.resources.notif_MEETING_REMINDER
import com.webrtcmeetpro.resources.notif_MEETING_SCHEDULED
import com.webrtcmeetpro.resources.notif_MEETING_STARTED
import com.webrtcmeetpro.resources.notif_TEAM_INVITATION
import com.webrtcmeetpro.resources.notif_TEAM_INVITATION_ACCEPTED
import com.webrtcmeetpro.resources.notif_empty
import com.webrtcmeetpro.resources.notif_mark_all
import com.webrtcmeetpro.resources.notif_title
import com.webrtcmeetpro.ui.DetailScaffold
import com.webrtcmeetpro.ui.ErrorState
import com.webrtcmeetpro.ui.Loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** 알림 종류별 문장 틀과 인자. 화면(stringResource)과 시스템 알림(getString)이 같이 쓴다 */
private fun template(n: AppNotification): Pair<StringResource, Array<Any>>? {
    val time = n.param("scheduledAt").takeIf { it.isNotEmpty() }?.let(::formatDateTime).orEmpty()
    return when (n.type) {
        "TEAM_INVITATION" -> Res.string.notif_TEAM_INVITATION to arrayOf(n.param("inviterName"), n.param("teamName"))
        "TEAM_INVITATION_ACCEPTED" -> Res.string.notif_TEAM_INVITATION_ACCEPTED to arrayOf(n.param("memberName"), n.param("teamName"))
        "CHAT_ROOM_INVITED" -> Res.string.notif_CHAT_ROOM_INVITED to arrayOf(n.param("inviterName"), n.param("roomName"))
        "MEETING_SCHEDULED" -> Res.string.notif_MEETING_SCHEDULED to arrayOf(n.param("teamName"), n.param("title"), time)
        "MEETING_REMINDER" -> Res.string.notif_MEETING_REMINDER to arrayOf(n.param("title"), time)
        "MEETING_STARTED" -> Res.string.notif_MEETING_STARTED to arrayOf(n.param("title"))
        "MEETING_CANCELED" -> Res.string.notif_MEETING_CANCELED to arrayOf(n.param("title"))
        else -> null
    }
}

@Composable
fun notificationText(n: AppNotification): String =
    template(n)?.let { (res, args) -> stringResource(res, *args) } ?: n.type

/** 컴포지션 밖(시스템 알림)에서 쓰는 버전 */
suspend fun notificationTextAsync(n: AppNotification): String =
    template(n)?.let { (res, args) -> getString(res, *args) } ?: n.type

/** 알림을 눌렀을 때 갈 앱 화면. 서버 link 는 웹 경로라서 앱 화면으로 바꾼다 */
sealed interface NotificationTarget {
    data class Meeting(val id: Long) : NotificationTarget
    data class ChatRoom(val id: Long) : NotificationTarget
    data class Team(val id: Long) : NotificationTarget
    data object Invitations : NotificationTarget
}

fun AppNotification.target(): NotificationTarget? {
    val parts = link.orEmpty().trim('/').split('/')
    return when {
        parts.getOrNull(1) == "meetings" -> parts.getOrNull(2)?.toLongOrNull()?.let(NotificationTarget::Meeting)
        parts.getOrNull(1) == "chat" -> parts.getOrNull(3)?.toLongOrNull()?.let(NotificationTarget::ChatRoom)
        parts.getOrNull(1) == "teams" -> parts.getOrNull(2)?.toLongOrNull()?.let(NotificationTarget::Team)
        parts.getOrNull(1) == "invitations" -> NotificationTarget.Invitations
        else -> null
    }
}

class NotificationsViewModel(
    private val notifications: NotificationRepository,
    private val badges: BadgeRepository,
    realtime: RealtimeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(Loadable<List<AppNotification>>())
    val state: StateFlow<Loadable<List<AppNotification>>> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            realtime.notifications.collect { n -> _state.update { it.copy(data = listOf(n) + it.data.orEmpty()) } }
        }
    }

    fun refresh() {
        _state.update { it.copy(loading = true, errorCode = null) }
        viewModelScope.launch {
            runCatching { notifications.recent() }
                .onSuccess { _state.value = Loadable(it, loading = false) }
                .onFailure { e -> _state.update { it.copy(loading = false, errorCode = e.errorCode) } }
        }
    }

    fun open(n: AppNotification) {
        if (n.read) return
        _state.update { s -> s.copy(data = s.data?.map { if (it.id == n.id) it.copy(read = true) else it }) }
        viewModelScope.launch {
            runCatching { notifications.markRead(n.id) }
            badges.refresh()
        }
    }

    fun markAll() {
        _state.update { s -> s.copy(data = s.data?.map { it.copy(read = true) }) }
        viewModelScope.launch {
            runCatching { notifications.markAllRead() }
            badges.refresh()
        }
    }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpen: (NotificationTarget) -> Unit,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val list = state.data

    DetailScaffold(
        title = stringResource(Res.string.notif_title),
        onBack = onBack,
        actions = {
            if (list.orEmpty().any { !it.read }) {
                TextButton(onClick = viewModel::markAll) { Text(stringResource(Res.string.notif_mark_all)) }
            }
        },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                list == null && state.errorCode != null -> ErrorState(state.errorCode!!, onRetry = viewModel::refresh)
                list == null -> CircularProgressIndicator(Modifier.padding(48.dp))
                list.isEmpty() -> EmptyState(Icons.Outlined.NotificationsNone, stringResource(Res.string.notif_empty))
                else -> LazyColumn(
                    Modifier.widthIn(max = 960.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(list, key = { it.id }) { n ->
                        NotificationRow(n) {
                            viewModel.open(n)
                            n.target()?.let(onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onClick: () -> Unit) {
    val icon = when {
        n.type.startsWith("MEETING") -> Icons.Outlined.CalendarMonth
        n.type.startsWith("CHAT") -> Icons.Outlined.ChatBubbleOutline
        else -> Icons.Outlined.GroupAdd
    }
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (n.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                notificationText(n),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (n.read) FontWeight.Normal else FontWeight.SemiBold,
            )
            Text(formatDateTime(n.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!n.read) {
            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}
