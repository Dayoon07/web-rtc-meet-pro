package com.webrtcmeetpro.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/** WebView2 런타임이 없는 PC 는 예전처럼 기본 브라우저로 연다 */
actual val inAppMeetingSupported: Boolean get() = WebView2.available

/**
 * 앱 창 안(Edge WebView2)에 회의 화면을 띄운다. 카메라·마이크는 WebView2 가 처음 한 번 허락을 묻는다.
 * 회의에서 나가면(웹이 /meet 밖으로 이동) 닫고, 웹뷰를 띄우지 못하면 브라우저로 넘긴다.
 */
@Composable
actual fun InAppMeeting(url: String, onClose: () -> Unit) {
    val close by rememberUpdatedState(onClose)
    val uriHandler = LocalUriHandler.current
    val panel = remember(url) {
        val done = AtomicBoolean(false)
        val finish: (Boolean) -> Unit = { openBrowser ->
            if (done.compareAndSet(false, true)) {
                SwingUtilities.invokeLater {
                    if (openBrowser) runCatching { uriHandler.openUri(url) }
                    close()
                }
            }
        }
        WebView2Panel(
            url = url,
            onPath = { path -> if (!path.startsWith("/meet/") && !path.startsWith("/oauth/")) finish(false) },
            onFailed = { finish(true) },
        )
    }
    DisposableEffect(panel) { onDispose { panel.close() } }
    SwingPanel(background = Color.Black, factory = { panel }, modifier = Modifier.fillMaxSize())
}
