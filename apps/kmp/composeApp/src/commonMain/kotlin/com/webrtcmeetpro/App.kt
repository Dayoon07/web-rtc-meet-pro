package com.webrtcmeetpro

import com.webrtcmeetpro.feature.auth.EmailVerifiedGate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.webrtcmeetpro.core.data.ChatRepository
import com.webrtcmeetpro.core.data.PushDevices
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
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
import com.webrtcmeetpro.core.designsystem.theme.MqTheme
import com.webrtcmeetpro.feature.auth.LoginScreen
import com.webrtcmeetpro.feature.auth.SignupScreen
import com.webrtcmeetpro.feature.chat.ChatPresence
import com.webrtcmeetpro.feature.chat.messagePreviewAsync
import com.webrtcmeetpro.feature.main.MainScreen
import com.webrtcmeetpro.feature.notifications.notificationTextAsync
import com.webrtcmeetpro.platform.LocalAppLocale
import com.webrtcmeetpro.platform.SystemNotifier
import com.webrtcmeetpro.platform.LocalHasTitleBar
import com.webrtcmeetpro.platform.WindowChromeColors
import com.webrtcmeetpro.platform.inQuietHours
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.app_name
import com.webrtcmeetpro.ui.UnexpectedErrorHost
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
    private val chat: ChatRepository,
    private val pushDevices: PushDevices,
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
                    away = false
                    lastActive = TimeSource.Monotonic.markNow()
                    launch { runCatching { users.refresh() } }
                    launch { pushEnabled = runCatching { pushDevices.register() }.getOrDefault(false) }
                    launch { watchIdle() }
                    // 다시 연결되면 서버는 새 연결을 "접속 중"으로 시작하므로 자리 비움이었으면 다시 알린다
                    launch { realtime.connected.collect { connected -> if (connected && away) sendAway(true) } }
                    launch { badges.refresh() }
                    launch { realtime.run() }
                    launch {
                        realtime.notifications.collect { n ->
                            badges.refresh()
                            // 지금 보고 있는 스레드의 답글은 띄우지 않는다
                            val watching = n.type == "THREAD_REPLY" && n.param("rootId").toLongOrNull() == presence.activeThreadId.value
                            if (!watching && !quietNow()) notifier.show(getString(Res.string.app_name), notificationTextAsync(n), n.link, n.type)
                        }
                    }
                    launch {
                        realtime.chatEvents.collect { event ->
                            badges.refresh()
                            // 새 메시지만 (고치기·지우기·반응은 알리지 않는다). 스레드 답글·나를 부른 멘션은 알림(THREAD_REPLY·CHAT_MENTION)으로 따로 온다
                            if (event.type != "MESSAGE") return@collect
                            val message = event.message ?: return@collect
                            val myId = users.me.value?.id
                            val mine = message.sender?.id == myId
                            val mentioned = message.mentionAll || (myId != null && myId in message.mentions)
                            // 앱이 뒤에 있으면 서버가 FCM 으로 보내므로 여기서도 띄우면 두 번 온다
                            val pushCovers = !foreground && pushEnabled
                            if (!mine && !mentioned && message.threadRootId == null && presence.activeRoomId.value != event.roomId && message.sender != null && !quietNow() && !pushCovers) {
                                notifier.show(message.sender!!.nickname, messagePreviewAsync(message), "/app/chat/0/${event.roomId}", "room-${event.roomId}")
                            }
                        }
                    }
                }
            }
        }
    }

    private var foreground = true
    private var away = false
    private var pushEnabled = false
    private var lastActive = TimeSource.Monotonic.markNow()

    /** Android 는 다른 앱으로 가면, Windows 는 창을 최소화하면 false */
    fun onForeground(visible: Boolean) {
        foreground = visible
        if (visible) onActivity() else setAway(true)
    }

    /** 화면을 만지거나 키를 누를 때마다 불린다. 자주 불리므로 시각만 적고, 자리 비움일 때만 서버에 알린다 */
    fun onActivity() {
        lastActive = TimeSource.Monotonic.markNow()
        if (away && foreground) setAway(false)
    }

    /** 웹과 같이 5분 동안 입력이 없으면 자리 비움 */
    private suspend fun watchIdle() {
        while (true) {
            delay(30.seconds)
            if (foreground && lastActive.elapsedNow() >= 5.minutes) setAway(true)
        }
    }

    private fun setAway(next: Boolean) {
        if (next == away || loggedIn.value != true) return
        away = next
        viewModelScope.launch { sendAway(next) }
    }

    private suspend fun sendAway(next: Boolean) {
        runCatching { chat.setAway(next) }.onFailure { if (away == next) away = !next }
    }

    /** 웹 푸시와 같이, 방해 금지 시간에는 시스템 알림만 띄우지 않는다 (알림 목록에는 쌓인다) */
    private fun quietNow(): Boolean {
        val me = users.me.value ?: return false
        val start = me.dndStart ?: return false
        val end = me.dndEnd ?: return false
        return inQuietHours(start, end, me.dndTimeZone)
    }
}

/**
 * Android(MainActivity)와 Desktop(main.kt)이 함께 쓰는 루트 화면.
 * titleBar: Windows 앱이 직접 그리는 타이틀 바 (앱 테마 색을 써야 해서 테마 안쪽에 둔다)
 */
@Composable
fun App(titleBar: (@Composable () -> Unit)? = null) {
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
            MqTheme(darkTheme = dark) {
                MaterialTheme.colorScheme.run { WindowChromeColors(surfaceContainerLow, onSurface, outlineVariant, dark) }
                Column(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalHasTitleBar provides (titleBar != null)) {
                        titleBar?.invoke()
                        Surface(
                            Modifier.weight(1f).fillMaxWidth()
                                // Initial 단계에서 보기만 하고 소비하지 않아 화면 안의 버튼·스크롤은 그대로 동작한다
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial)
                                            viewModel.onActivity()
                                        }
                                    }
                                }
                                .onPreviewKeyEvent { viewModel.onActivity(); false },
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            when (if (settings == null) null else loggedIn) {
                                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                false -> AuthFlow(LocalAppLocale.current)
                                // 이메일 가입 후 인증 전이면 메인 화면 대신 인증 안내
                                true -> {
                                    AwayTracker(viewModel)
                                    EmailVerifiedGate { MainScreen() }
                                }
                            }
                            // 서버 500: 에러 코드 + 문의 이메일 안내 (로그인 전후 어느 화면에서든)
                            UnexpectedErrorHost()
                        }
                    }
                }
            }
        }
    }
}

/** 앱이 화면에서 사라지면(Android 백그라운드, Windows 최소화) 자리 비움으로 알린다 */
@Composable
private fun AwayTracker(viewModel: AppViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val visible = state.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(visible) { viewModel.onForeground(visible) }
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
