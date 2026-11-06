package com.webrtcmeetpro

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.webrtcmeetpro.di.initKoin
import com.webrtcmeetpro.platform.androidPlatformModule
import org.koin.android.ext.koin.androidContext

/** Android 진입점. 화면은 전부 commonMain 의 App() */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

class LimeetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(androidPlatformModule(this)) { androidContext(this@LimeetApplication) }
    }
}
