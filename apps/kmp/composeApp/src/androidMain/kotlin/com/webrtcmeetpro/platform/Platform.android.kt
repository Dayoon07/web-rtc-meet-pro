package com.webrtcmeetpro.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.webrtcmeetpro.core.data.PREFERENCES_FILE_NAME
import com.webrtcmeetpro.core.data.createPreferencesDataStore
import com.webrtcmeetpro.core.network.ApiConfig
import org.koin.dsl.module
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

actual object LocalAppLocale {
    private var default: Locale? = null

    actual val current: String
        @Composable get() = Locale.getDefault().toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        if (default == null) default = Locale.getDefault()
        val locale = value?.let(Locale::forLanguageTag) ?: default!!
        Locale.setDefault(locale)
        configuration.setLocale(locale)
        val resources = LocalContext.current.resources
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration.provides(configuration)
    }
}

actual fun formatDateTime(iso: String): String = runCatching {
    OffsetDateTime.parse(iso)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()))
}.getOrDefault(iso)

/**
 * Android 전용 값.
 * 에뮬레이터에서 PC 의 localhost 는 10.0.2.2. 실기기는 PC 의 LAN IP 로 바꾼다.
 */
fun androidPlatformModule(context: Context) = module {
    single { ApiConfig(baseUrl = "http://10.0.2.2:8080") }
    single { createPreferencesDataStore(context.filesDir.resolve(PREFERENCES_FILE_NAME).absolutePath) }
}
