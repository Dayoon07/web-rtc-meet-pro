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
import com.webrtcmeetpro.core.data.AppSettings
import com.webrtcmeetpro.core.data.AuthRepository
import com.webrtcmeetpro.core.data.SettingsStore
import com.webrtcmeetpro.core.data.ThemeMode
import com.webrtcmeetpro.core.designsystem.theme.LimeetTheme
import com.webrtcmeetpro.feature.auth.LoginScreen
import com.webrtcmeetpro.feature.main.MainScreen
import com.webrtcmeetpro.platform.LocalAppLocale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel

/** 앱 전체 상태: 로그인 여부(토큰 유무)와 테마·언어 설정. null 은 아직 저장소를 읽는 중 */
class AppViewModel(auth: AuthRepository, settingsStore: SettingsStore) : ViewModel() {
    val loggedIn: StateFlow<Boolean?> = auth.isLoggedIn.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val settings: StateFlow<AppSettings?> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
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
                        false -> LoginScreen()
                        true -> MainScreen()
                    }
                }
            }
        }
    }
}
