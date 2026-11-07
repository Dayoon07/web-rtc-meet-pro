package com.webrtcmeetpro.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

actual val inAppMeetingSupported: Boolean = true

/**
 * 앱 안 웹뷰로 회의 화면을 띄운다. 웹 페이지가 카메라·마이크를 달라고 하면 Android 권한부터 묻고 넘겨준다.
 * 화면이 꺼지지 않게 하고, 회의에서 나가면(웹이 /meet 밖으로 이동) 닫는다.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun InAppMeeting(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val close by rememberUpdatedState(onClose)
    var pending by remember { mutableStateOf<PermissionRequest?>(null) }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val request = pending ?: return@rememberLauncherForActivityResult
        pending = null
        // 허락받은 것만 웹에 넘긴다 (카메라는 거절하고 마이크만 허락해도 회의는 된다)
        val granted = request.resources.filter { r -> permissionsFor(r).all { p -> context.has(p) } }
        if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray())
    }
    BackHandler { close() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                keepScreenOn = true
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // 다른 사람 소리가 누르지 않아도 바로 나오게
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        post {
                            val missing = request.resources.flatMap(::permissionsFor).distinct().filterNot { ctx.has(it) }
                            if (missing.isEmpty()) {
                                request.grant(request.resources)
                            } else {
                                pending = request
                                askPermissions.launch(missing.toTypedArray())
                            }
                        }
                    }
                }
                webViewClient = object : WebViewClient() {
                    // 웹은 화면 이동을 history API 로 한다 → 주소가 바뀔 때마다 여기로 온다
                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        val path = Uri.parse(url).path.orEmpty()
                        if (!path.startsWith("/meet/") && !path.startsWith("/oauth/")) close()
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun android.content.Context.has(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun permissionsFor(resource: String): List<String> = when (resource) {
    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> listOf(Manifest.permission.CAMERA)
    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> listOf(Manifest.permission.RECORD_AUDIO)
    else -> emptyList()
}
