package com.webrtcmeetpro

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.webrtcmeetpro.core.network.LimeetApi
import com.webrtcmeetpro.di.initKoin
import com.webrtcmeetpro.platform.AppErrorReporter
import com.webrtcmeetpro.platform.appDataDir
import com.webrtcmeetpro.platform.desktopPlatformModule
import java.awt.Dimension

/** Windows 데스크톱 진입점. ./gradlew :composeApp:run */
fun main() {
    val koin = initKoin(desktopPlatformModule()).koin
    // 앱 오류를 서버 에러 기록으로 (앱이 죽으면 다음 실행 때 보낸다)
    AppErrorReporter.install(koin.get<LimeetApi>(), appDataDir().absolutePath, "windows")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Limeet Pro",
            state = rememberWindowState(size = DpSize(1180.dp, 780.dp), position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center)),
        ) {
            window.minimumSize = Dimension(400, 600)
            App()
        }
    }
}
