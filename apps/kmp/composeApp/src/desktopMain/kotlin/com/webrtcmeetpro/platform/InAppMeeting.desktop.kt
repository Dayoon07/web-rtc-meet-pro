package com.webrtcmeetpro.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalUriHandler

/** Windows 는 회의를 기본 브라우저로 연다 (앱 안 회의 화면 없음) */
actual val inAppMeetingSupported: Boolean = false

/** 쓰이지 않지만, 혹시 불리면 브라우저로 열고 바로 닫는다 */
@Composable
actual fun InAppMeeting(url: String, onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(url) {
        uriHandler.openUri(url)
        onClose()
    }
}
