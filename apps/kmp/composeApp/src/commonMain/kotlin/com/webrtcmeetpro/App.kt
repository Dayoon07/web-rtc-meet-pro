package com.webrtcmeetpro

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.webrtcmeetpro.core.data.AppSettings
import com.webrtcmeetpro.core.data.AuthRepository
import com.webrtcmeetpro.core.data.BadgeRepository
import com.webrtcmeetpro.core.data.RealtimeRepository
import com.webrtcmeetpro.core.data.SettingsStore
import com.webrtcmeetpro.core.data.ThemeMode
import com.webrtcmeetpro.core.data.UserRepository
import com.webrtcmeetpro.core.designsystem.theme.LimeetTheme
import com.webrtcmeetpro.feature.auth.LoginScreen
import com.webrtcmeetpro.feature.auth.SignupScreen
import com.webrtcmeetpro.feature.chat.ChatPresence
import com.webrtcmeetpro.feature.main.MainScreen
import com.webrtcmeetpro.feature.notifications.notificationTextAsync
import com.webrtcmeetpro.platform.LocalAppLocale
import com.webrtcmeetpro.platform.SystemNotifier
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.app_name
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * 앱 전체 상태: 로그인 여부(토큰 유무)와 테마·언어 설정. null 은 아직 저장소를 읽는 중.
 * 로그인해 있는 동안 실시간 연결(STOMP)을 유지하고, 알림·새 메시지를 시스템 알림으로 띄운다.
 */
class AppViewModel(
    auth: AuthRepository,
    settingsStore: SettingsStore,
    private val users: UserRepository,
    private val realtime: RealtimeRepository,
    private val badges: BadgeRepository,
    private val notifier: SystemNotifier,
    private val presence: ChatPresence,
) : ViewModel() {
    val loggedIn: StateFlow<Boolean?> = auth.isLoggedIn.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings: StateFlow<AppSettings?> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            // 로그인 → 연결·배지·알림 시작, 로그아웃 → collectLatest 가 이전 블록을 취소해 모두 정리
            loggedIn.collectLatest { inSession ->
                if (inSession != true) {
                    badges.clear()
                    return@collectLatest
                }
                coroutineScope {
                    launch { runCatching { users.refresh() } }
                    launch { badges.refresh() }
                    launch { realtime.run() }
                    launch {
                        realtime.notifications.collect { n ->
                            badges.refresh()
                            notifier.show(getString(Res.string.app_name), notificationTextAsync(n))
                        }
                    }
                    launch {
                        realtime.chatEvents.collect { event ->
                            badges.refresh()
                            val message = event.message ?: return@collect
                            val mine = message.sender?.id == users.me.value?.id
                            if (!mine && presence.activeRoomId.value != event.roomId && message.sender != null) {
                                notifier.show(message.sender!!.nickname, message.content)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Android(MainActivity)와 Desktop(main.kt)이 함께 쓰는 루트 화면 */
@Composable
fun App() {
    val viewModel = koinViewModel<AppViewModel>()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val current = settings ?: AppSettings()

    CompositionLocalProvider(LocalAppLocale provides current.language?.tag) {
        // 언어가 바뀌면 화면 전체를 다시 그려 문자열 리소스를 새 언어로 읽는다
        key(current.language) {
            val dark = when (current.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LimeetTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (if (settings == null) null else loggedIn) {
                        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        false -> AuthFlow(LocalAppLocale.current)
                        true -> MainScreen()
                    }
                }
            }
        }
    }
}

// 네비게이션 경로는 private 이면 안 된다 (직렬화기를 리플렉션으로 찾다가 실패)
@Serializable data object LoginRoute
@Serializable data object SignupRoute

/**
 * 로그인 전: 로그인 ↔ 회원가입 (NavHost 라 Android 뒤로 가기도 동작).
 * 가입·로그인이 성공하면 토큰이 저장되고 App 이 MainScreen 으로 바꾼다.
 */
@Composable
private fun AuthFlow(languageTag: String) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = LoginRoute) {
        composable<LoginRoute> { LoginScreen(onSignup = { nav.navigate(SignupRoute) }) }
        composable<SignupRoute> { SignupScreen(onBack = { nav.popBackStack() }, languageTag = languageTag) }
    }
}
