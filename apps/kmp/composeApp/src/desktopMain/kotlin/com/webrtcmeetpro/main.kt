package com.webrtcmeetpro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.webrtcmeetpro.core.network.MqApi
import com.webrtcmeetpro.di.initKoin
import com.webrtcmeetpro.platform.AppErrorReporter
import com.webrtcmeetpro.platform.DesktopTray
import com.webrtcmeetpro.platform.DesktopWindow
import com.webrtcmeetpro.platform.appDataDir
import com.webrtcmeetpro.platform.desktopPlatformModule
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.tray_open
import com.webrtcmeetpro.resources.tray_quit
import com.webrtcmeetpro.resources.tray_running
import com.webrtcmeetpro.ui.MqMark
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.runBlocking
import java.awt.Dimension

/**
 * Windows 데스크톱 진입점. ./gradlew :composeApp:run
 * Windows 에는 앱이 꺼져 있을 때 알림을 대신 받아 주는 곳이 없어서, 창을 닫으면 트레이로 숨기고 연결을 유지한다.
 */
fun main() {
    val koin = initKoin(desktopPlatformModule()).koin
    // 앱 오류를 서버 에러 기록으로 (앱이 죽으면 다음 실행 때 보낸다)
    AppErrorReporter.install(koin.get<MqApi>(), appDataDir().absolutePath, "windows")
    application {
        var visible by remember { mutableStateOf(true) }
        var toldAboutTray by remember { mutableStateOf(false) }
        val logo = rememberVectorPainter(MqMark)
        val trayIcon = remember(logo) { TintedPainter(logo, Color(0xFF7C1FFF)) }

        Tray(
            icon = trayIcon,
            state = DesktopTray.state,
            tooltip = "MQ",
            // 아이콘을 두 번 누르거나 알림 풍선을 누르면 창을 다시 띄운다
            onAction = { visible = true },
            menu = {
                Item(stringResource(Res.string.tray_open), onClick = { visible = true })
                Item(stringResource(Res.string.tray_quit), onClick = ::exitApplication)
            },
        )

        Window(
            onCloseRequest = {
                visible = false
                if (!toldAboutTray) {
                    toldAboutTray = true
                    val message = runBlocking { getString(Res.string.tray_running) }
                    DesktopTray.state.sendNotification(Notification("MQ", message, Notification.Type.Info))
                }
            },
            visible = visible,
            title = "MQ",
            state = rememberWindowState(size = DpSize(1180.dp, 780.dp), position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center)),
        ) {
            window.minimumSize = Dimension(400, 600)
            window.iconImages = DesktopWindow.icons
            DesktopWindow.window = window
            App()
        }
    }
}

/** 로고(검은색 벡터)를 브랜드 색으로 칠해서 트레이에 그린다 */
private class TintedPainter(private val vector: VectorPainter, private val color: Color) : Painter() {
    override val intrinsicSize: Size get() = vector.intrinsicSize

    override fun DrawScope.onDraw() {
        with(vector) { draw(size, colorFilter = ColorFilter.tint(color)) }
    }
}
