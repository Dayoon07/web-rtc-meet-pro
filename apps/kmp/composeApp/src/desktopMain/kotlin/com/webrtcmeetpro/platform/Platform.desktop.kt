package com.webrtcmeetpro.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.webrtcmeetpro.BuildEnv
import com.webrtcmeetpro.core.data.PREFERENCES_FILE_NAME
import com.webrtcmeetpro.core.data.createPreferencesDataStore
import com.webrtcmeetpro.core.network.ApiConfig
import org.koin.dsl.module
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import com.webrtcmeetpro.core.data.NoPushDevices
import com.webrtcmeetpro.core.data.PushDevices
import java.io.File
import java.util.Locale

actual object LocalAppLocale {
    private var default: Locale? = null
    private val LocalLocale = staticCompositionLocalOf { Locale.getDefault().toString() }

    actual val current: String
        @Composable get() = LocalLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        if (default == null) default = Locale.getDefault()
        val locale = value?.let(Locale::forLanguageTag) ?: default!!
        Locale.setDefault(locale)
        return LocalLocale.provides(locale.toString())
    }
}

/** main.kt 의 트레이 아이콘과 같은 것을 써야 아이콘이 두 개 생기지 않는다 */
object DesktopTray {
    val state = TrayState()
}

/** Windows 알림 센터. 창을 닫아도 트레이에서 계속 돌아서 알림을 받는다 (Windows 는 앱이 꺼지면 받을 방법이 없다) */
class DesktopNotifier : SystemNotifier {
    override fun show(title: String, body: String, link: String?, tag: String?) {
        DesktopTray.state.sendNotification(Notification(title, body, Notification.Type.Info))
    }
}

/** Windows: %APPDATA%\MQ, 그 밖: ~/.mq */
fun appDataDir(): File {
    val appData = System.getenv("APPDATA")
    val dir = if (appData != null) File(appData, "MQ") else File(System.getProperty("user.home"), ".mq")
    return dir.apply { mkdirs() }
}

/**
 * 서버 주소 우선순위: -Dmq.api=… > MQ_API_URL > 빌드 때 넣은 BuildEnv > localhost 개발 서버
 * (웹 주소도 같은 방식: -Dmq.web / MQ_WEB_URL)
 */
fun desktopPlatformModule() = module {
    single {
        ApiConfig(
            baseUrl = System.getProperty("mq.api") ?: System.getenv("MQ_API_URL")
                ?: BuildEnv.API_URL.ifEmpty { "http://localhost:8080" },
            webUrl = System.getProperty("mq.web") ?: System.getenv("MQ_WEB_URL")
                ?: BuildEnv.WEB_URL.ifEmpty { "http://localhost:5173" },
            // 앱 언어를 바꾸면 LocalAppLocale 이 Locale.setDefault 를 바꾼다
            language = { Locale.getDefault().language },
            userAgent = "MQ-App (${System.getProperty("os.name")} ${System.getProperty("os.version")}; Java ${System.getProperty("java.version")})",
        )
    }
    single { createPreferencesDataStore(File(appDataDir(), PREFERENCES_FILE_NAME).absolutePath) }
    single<SystemNotifier> { DesktopNotifier() }
    single<GoogleLoginLauncher> { DesktopGoogleLogin() }
    single<PushDevices> { NoPushDevices }
}
