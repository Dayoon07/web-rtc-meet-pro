package com.webrtcmeetpro

import android.content.Intent
import com.webrtcmeetpro.platform.OAuthCallbacks
import com.webrtcmeetpro.platform.PendingLinks
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.webrtcmeetpro.core.network.MqApi
import com.webrtcmeetpro.di.initKoin
import com.webrtcmeetpro.platform.AppErrorReporter
import com.webrtcmeetpro.platform.androidPlatformModule
import org.koin.android.ext.koin.androidContext

/** Android 진입점. 화면은 전부 commonMain 의 App() */
class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Android 13+ 는 회의 알림·새 메시지 알림을 띄우려면 권한이 필요하다
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handle(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** 구글 로그인에서 돌아왔거나(mq://oauth) 알림을 눌러서 열렸을 때 */
    private fun handle(intent: Intent?) {
        val data = intent?.data
        if (data?.scheme == "mq" && data.host == "oauth") OAuthCallbacks.deliver(data.toString())
        PendingLinks.open(intent?.getStringExtra(EXTRA_LINK))
    }

    companion object {
        const val EXTRA_LINK = "com.webrtcmeetpro.link"
    }
}

class MqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = initKoin(androidPlatformModule(this)) { androidContext(this@MqApplication) }.koin
        // 앱 오류를 서버 에러 기록으로 (앱이 죽으면 다음 실행 때 보낸다)
        AppErrorReporter.install(koin.get<MqApi>(), filesDir.absolutePath, "android")
    }
}
