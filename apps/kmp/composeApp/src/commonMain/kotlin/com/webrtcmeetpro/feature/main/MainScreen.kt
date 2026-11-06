package com.webrtcmeetpro.feature.main

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.webrtcmeetpro.feature.home.HomeScreen
import com.webrtcmeetpro.feature.meetings.MeetingsScreen
import com.webrtcmeetpro.feature.settings.SettingsScreen
import com.webrtcmeetpro.feature.teams.TeamsScreen
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.nav_home
import com.webrtcmeetpro.resources.nav_meetings
import com.webrtcmeetpro.resources.nav_settings
import com.webrtcmeetpro.resources.nav_teams
import com.webrtcmeetpro.ui.BrandLogo
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.reflect.KClass

@Serializable data object HomeRoute
@Serializable data object TeamsRoute
@Serializable data object MeetingsRoute
@Serializable data object SettingsRoute

private enum class Tab(val route: Any, val routeClass: KClass<*>, val label: StringResource, val icon: ImageVector) {
    HOME(HomeRoute, HomeRoute::class, Res.string.nav_home, Icons.Outlined.Home),
    TEAMS(TeamsRoute, TeamsRoute::class, Res.string.nav_teams, Icons.Outlined.Groups),
    MEETINGS(MeetingsRoute, MeetingsRoute::class, Res.string.nav_meetings, Icons.Outlined.CalendarMonth),
    SETTINGS(SettingsRoute, SettingsRoute::class, Res.string.nav_settings, Icons.Outlined.Settings),
}

/** 탭 이동: 같은 탭을 여러 번 쌓지 않고, 탭별 스크롤 상태는 유지 */
private fun NavHostController.openTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * 로그인 후 메인. 같은 코드로 폭에 따라 모양만 바뀐다.
 * 폰(좁은 화면): 하단 NavigationBar / 태블릿·데스크톱 창(넓은 화면): 왼쪽 NavigationRail
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val selected = Tab.entries.firstOrNull { tab ->
        backStack?.destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
    } ?: Tab.HOME

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Scaffold(
            bottomBar = {
                if (!wide) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == selected,
                                onClick = { navController.openTab(tab) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
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
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                NavHost(navController, startDestination = HomeRoute, modifier = Modifier.weight(1f)) {
                    composable<HomeRoute> {
                        HomeScreen(
                            onOpenMeetings = { navController.openTab(Tab.MEETINGS) },
                            onOpenTeams = { navController.openTab(Tab.TEAMS) },
                        )
                    }
                    composable<TeamsRoute> { TeamsScreen() }
                    composable<MeetingsRoute> { MeetingsScreen() }
                    composable<SettingsRoute> { SettingsScreen() }
                }
            }
        }
    }
}
