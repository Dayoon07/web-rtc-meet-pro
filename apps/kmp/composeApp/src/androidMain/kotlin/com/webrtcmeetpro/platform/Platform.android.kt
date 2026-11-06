package com.webrtcmeetpro.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.webrtcmeetpro.BuildEnv
import com.webrtcmeetpro.MainActivity
import com.webrtcmeetpro.core.data.PREFERENCES_FILE_NAME
import com.webrtcmeetpro.core.data.createPreferencesDataStore
import com.webrtcmeetpro.core.network.ApiConfig
import org.koin.dsl.module
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

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

/** Android 알림. 13 이상은 POST_NOTIFICATIONS 권한이 있어야 보인다 (MainActivity 가 처음 실행 때 요청) */
class AndroidNotifier(private val context: Context) : SystemNotifier {
    private val ids = AtomicInteger(1)

    init {
        val channel = NotificationChannel(CHANNEL, "Limeet Pro", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun show(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ids.getAndIncrement(), notification)
    }

    private companion object {
        const val CHANNEL = "limeet"
    }
}

/**
 * Android 전용 값. 운영 빌드는 -Plimeet.apiUrl 로 넣은 주소(BuildEnv)를 쓴다.
 * 개발: 에뮬레이터에서 PC 의 localhost 는 10.0.2.2. 실기기는 PC 의 LAN IP 로 바꾼다.
 * 웹 주소는 localhost: 브라우저는 https 또는 localhost 에서만 카메라를 허용하므로
 * 개발 중엔 `adb reverse tcp:5173 tcp:5173` 으로 PC 의 5173 을 에뮬레이터 localhost 로 연결해 쓴다.
 */
fun androidPlatformModule(context: Context) = module {
    single {
        ApiConfig(
            baseUrl = BuildEnv.API_URL.ifEmpty { "http://10.0.2.2:8080" },
            webUrl = BuildEnv.WEB_URL.ifEmpty { "http://localhost:5173" },
        )
    }
    single { createPreferencesDataStore(context.filesDir.resolve(PREFERENCES_FILE_NAME).absolutePath) }
    single<SystemNotifier> { AndroidNotifier(context) }
}
