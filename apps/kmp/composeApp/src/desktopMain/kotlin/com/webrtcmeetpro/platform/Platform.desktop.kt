package com.webrtcmeetpro.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.webrtcmeetpro.BuildEnv
import com.webrtcmeetpro.core.data.PREFERENCES_FILE_NAME
import com.webrtcmeetpro.core.data.createPreferencesDataStore
import com.webrtcmeetpro.core.network.ApiConfig
import org.koin.dsl.module
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage
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

/** Windows 알림 센터 풍선. 트레이를 지원하지 않는 환경이면 조용히 무시 */
class DesktopNotifier : SystemNotifier {
    private val icon: TrayIcon? by lazy {
        if (!SystemTray.isSupported()) return@lazy null
        runCatching {
            val image = javaClass.getResource("/tray.png")?.let { Toolkit.getDefaultToolkit().getImage(it) }
                ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB).apply {
                    // 리소스가 없으면 보라색 사각형
                    for (x in 0 until 16) for (y in 0 until 16) setRGB(x, y, 0xFF7C3AED.toInt())
                }
            TrayIcon(image, "Limeet Pro").apply { isImageAutoSize = true }.also { SystemTray.getSystemTray().add(it) }
        }.getOrNull()
    }

    override fun show(title: String, body: String) {
        icon?.displayMessage(title, body, TrayIcon.MessageType.INFO)
    }
}

/** Windows: %APPDATA%\LimeetPro, 그 밖: ~/.limeetpro */
private fun appDataDir(): File {
    val appData = System.getenv("APPDATA")
    val dir = if (appData != null) File(appData, "LimeetPro") else File(System.getProperty("user.home"), ".limeetpro")
    return dir.apply { mkdirs() }
}

/**
 * 서버 주소 우선순위: -Dlimeet.api=… > LIMEET_API_URL > 빌드 때 넣은 BuildEnv > localhost 개발 서버
 * (웹 주소도 같은 방식: -Dlimeet.web / LIMEET_WEB_URL)
 */
fun desktopPlatformModule() = module {
    single {
        ApiConfig(
            baseUrl = System.getProperty("limeet.api") ?: System.getenv("LIMEET_API_URL")
                ?: BuildEnv.API_URL.ifEmpty { "http://localhost:8080" },
            webUrl = System.getProperty("limeet.web") ?: System.getenv("LIMEET_WEB_URL")
                ?: BuildEnv.WEB_URL.ifEmpty { "http://localhost:5173" },
        )
    }
    single { createPreferencesDataStore(File(appDataDir(), PREFERENCES_FILE_NAME).absolutePath) }
    single<SystemNotifier> { DesktopNotifier() }
}
