package com.webrtcmeetpro

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.webrtcmeetpro.di.initKoin
import com.webrtcmeetpro.platform.desktopPlatformModule
import java.awt.Dimension

/** Windows 데스크톱 진입점. ./gradlew :composeApp:run */
fun main() {
    initKoin(desktopPlatformModule())
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
