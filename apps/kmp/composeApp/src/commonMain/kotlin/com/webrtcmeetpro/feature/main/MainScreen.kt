package com.webrtcmeetpro.feature.main

import com.webrtcmeetpro.platform.InAppMeeting
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.webrtcmeetpro.core.data.BadgeRepository
import com.webrtcmeetpro.feature.chat.ChatListScreen
import com.webrtcmeetpro.feature.chat.ChatRoomScreen
import com.webrtcmeetpro.feature.chat.NewChatScreen
import com.webrtcmeetpro.feature.home.HomeScreen
import com.webrtcmeetpro.feature.meetings.MeetingDetailScreen
import com.webrtcmeetpro.feature.meetings.MeetingsScreen
import com.webrtcmeetpro.feature.meetings.ScheduleMeetingScreen
import com.webrtcmeetpro.feature.notifications.NotificationTarget
import com.webrtcmeetpro.feature.notifications.NotificationsScreen
import com.webrtcmeetpro.feature.settings.SettingsScreen
import com.webrtcmeetpro.feature.teams.TeamDetailScreen
import com.webrtcmeetpro.feature.teams.TeamsScreen
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.nav_chat
import com.webrtcmeetpro.resources.nav_home
import com.webrtcmeetpro.resources.nav_meetings
import com.webrtcmeetpro.resources.nav_settings
import com.webrtcmeetpro.resources.nav_teams
import com.webrtcmeetpro.ui.BrandLogo
import com.webrtcmeetpro.ui.LocalSnackbar
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.reflect.KClass

// 탭(최상위) 화면
@Serializable data object HomeRoute
@Serializable data object ChatRoute
@Serializable data object MeetingsRoute
@Serializable data object TeamsRoute
@Serializable data object SettingsRoute

// 하위 화면
@Serializable data class ChatRoomRoute(val roomId: Long)
@Serializable data class NewChatRoute(val teamId: Long)
@Serializable data class MeetingDetailRoute(val meetingId: Long)
@Serializable data object ScheduleMeetingRoute
@Serializable data class EditMeetingRoute(val meetingId: Long)
/** Android 앱 안 회의 화면 (url = 웹 로그인 넘김 주소) */
@Serializable data class MeetRoomRoute(val url: String)
@Serializable data class TeamDetailRoute(val teamId: Long)
@Serializable data object NotificationsRoute

private enum class Tab(val route: Any, val routeClass: KClass<*>, val label: StringResource, val icon: ImageVector) {
    HOME(HomeRoute, HomeRoute::class, Res.string.nav_home, Icons.Outlined.Home),
    CHAT(ChatRoute, ChatRoute::class, Res.string.nav_chat, Icons.Outlined.ChatBubbleOutline),
    MEETINGS(MeetingsRoute, MeetingsRoute::class, Res.string.nav_meetings, Icons.Outlined.CalendarMonth),
    TEAMS(TeamsRoute, TeamsRoute::class, Res.string.nav_teams, Icons.Outlined.Groups),
    SETTINGS(SettingsRoute, SettingsRoute::class, Res.string.nav_settings, Icons.Outlined.Settings),
}

private fun NavDestination?.tab(): Tab? = Tab.entries.firstOrNull { tab -> this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }

/** 탭 이동: 같은 탭을 여러 번 쌓지 않고, 탭별 스크롤 상태는 유지 */
private fun NavHostController.openTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * 로그인 후 메인. 같은 코드로 폭에 따라 모양만 바뀐다.
 * 폰(좁은 화면): 하단 NavigationBar (하위 화면에서는 숨김) / 태블릿·데스크톱 창: 왼쪽 NavigationRail
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = backStack?.destination.tab()
    // 하위 화면(채팅방 등)에 있을 때는 들어오기 전 탭을 계속 강조
    var lastTab by remember { mutableStateOf(Tab.HOME) }
    if (currentTab != null) lastTab = currentTab
    val selected = currentTab ?: lastTab
    val badges = koinInject<BadgeRepository>()
    val chatUnread by badges.chatUnread.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val badgeCount: (Tab) -> Long = { if (it == Tab.CHAT) chatUnread else 0 }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide && currentTab != null) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                            Tab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = tab == selected,
                                    onClick = { navController.openTab(tab) },
                                    icon = { TabIcon(tab, badgeCount(tab)) },
                                    label = { Text(stringResource(tab.label)) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    if (wide) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            windowInsets = WindowInsets(0),
                            header = {
                                Spacer(Modifier.height(12.dp))
                                BrandLogo(showName = false, size = 36.dp)
                                Spacer(Modifier.height(16.dp))
                            },
                        ) {
                            Tab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = tab == selected,
                                    onClick = { navController.openTab(tab) },
                                    icon = { TabIcon(tab, badgeCount(tab)) },
                                    label = { Text(stringResource(tab.label)) },
                                )
                            }
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    MainNavHost(navController, Modifier.weight(1f))
                }
            }
            PlanExpiryDialog()
        }
    }
}

@Composable
private fun TabIcon(tab: Tab, count: Long) {
    BadgedBox(badge = { if (count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) } }) {
        Icon(tab.icon, contentDescription = null)
    }
}

@Composable
private fun MainNavHost(nav: NavHostController, modifier: Modifier) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenMeetings = { nav.openTab(Tab.MEETINGS) },
                onOpenTeams = { nav.openTab(Tab.TEAMS) },
                onOpenMeeting = { nav.navigate(MeetingDetailRoute(it)) },
                onOpenTeam = { nav.navigate(TeamDetailRoute(it)) },
                onOpenNotifications = { nav.navigate(NotificationsRoute) },
                onSchedule = { nav.navigate(ScheduleMeetingRoute) },
            )
        }
        composable<ChatRoute> {
            ChatListScreen(
                onOpenRoom = { nav.navigate(ChatRoomRoute(it)) },
                onNewChat = { nav.navigate(NewChatRoute(it)) },
            )
        }
        composable<MeetingsRoute> {
            MeetingsScreen(onOpen = { nav.navigate(MeetingDetailRoute(it)) }, onSchedule = { nav.navigate(ScheduleMeetingRoute) })
        }
        composable<TeamsRoute> { TeamsScreen(onOpen = { nav.navigate(TeamDetailRoute(it)) }) }
        composable<SettingsRoute> { SettingsScreen() }

        composable<ChatRoomRoute> { ChatRoomScreen(it.toRoute<ChatRoomRoute>().roomId, onBack = back) }
        composable<NewChatRoute> {
            NewChatScreen(
                teamId = it.toRoute<NewChatRoute>().teamId,
                onBack = back,
                // 새 대화 화면은 스택에서 빼고 방으로 이동 (뒤로 가면 채팅 목록)
                onOpened = { roomId -> nav.navigate(ChatRoomRoute(roomId)) { popUpTo<NewChatRoute> { inclusive = true } } },
            )
        }
        composable<MeetingDetailRoute> {
            MeetingDetailScreen(
                it.toRoute<MeetingDetailRoute>().meetingId,
                onBack = back,
                onEdit = { id -> nav.navigate(EditMeetingRoute(id)) },
                onJoinInApp = { url -> nav.navigate(MeetRoomRoute(url)) },
            )
        }
        composable<MeetRoomRoute> {
            InAppMeeting(it.toRoute<MeetRoomRoute>().url, onClose = back)
        }
        composable<EditMeetingRoute> {
            ScheduleMeetingScreen(
                onBack = back,
                editMeetingId = it.toRoute<EditMeetingRoute>().meetingId,
                // 고친 내용으로 상세를 다시 연다 (예전 상세 화면은 스택에서 뺀다)
                onCreated = { id -> nav.navigate(MeetingDetailRoute(id)) { popUpTo<MeetingDetailRoute> { inclusive = true } } },
            )
        }
        composable<ScheduleMeetingRoute> {
            ScheduleMeetingScreen(
                onBack = back,
                onCreated = { id -> nav.navigate(MeetingDetailRoute(id)) { popUpTo<ScheduleMeetingRoute> { inclusive = true } } },
            )
        }
        composable<TeamDetailRoute> { TeamDetailScreen(it.toRoute<TeamDetailRoute>().teamId, onBack = back) }
        composable<NotificationsRoute> {
            NotificationsScreen(
                onBack = back,
                onOpen = { target ->
                    when (target) {
                        is NotificationTarget.Meeting -> nav.navigate(MeetingDetailRoute(target.id))
                        is NotificationTarget.ChatRoom -> nav.navigate(ChatRoomRoute(target.id))
                        is NotificationTarget.Team -> nav.navigate(TeamDetailRoute(target.id))
                        NotificationTarget.Invitations -> nav.openTab(Tab.TEAMS)
                    }
                },
            )
        }
    }
}
