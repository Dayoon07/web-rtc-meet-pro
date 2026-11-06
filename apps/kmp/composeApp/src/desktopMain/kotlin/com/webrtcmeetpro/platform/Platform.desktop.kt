package com.webrtcmeetpro.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.webrtcmeetpro.core.data.PREFERENCES_FILE_NAME
import com.webrtcmeetpro.core.data.createPreferencesDataStore
import com.webrtcmeetpro.core.network.ApiConfig
import org.koin.dsl.module
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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

actual fun formatDateTime(iso: String): String = runCatching {
    OffsetDateTime.parse(iso)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()))
}.getOrDefault(iso)

/** Windows: %APPDATA%\LimeetPro, 그 밖: ~/.limeetpro */
private fun appDataDir(): File {
    val appData = System.getenv("APPDATA")
    val dir = if (appData != null) File(appData, "LimeetPro") else File(System.getProperty("user.home"), ".limeetpro")
    return dir.apply { mkdirs() }
}

/** 서버 주소는 -Dlimeet.api=... 또는 LIMEET_API_URL 로 바꿀 수 있다 */
fun desktopPlatformModule() = module {
    single {
        ApiConfig(
            baseUrl = System.getProperty("limeet.api") ?: System.getenv("LIMEET_API_URL") ?: "http://localhost:8080",
        )
    }
    single { createPreferencesDataStore(File(appDataDir(), PREFERENCES_FILE_NAME).absolutePath) }
}
