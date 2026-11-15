package com.webrtcmeetpro.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.webrtcmeetpro.core.data.PushDevices
import com.webrtcmeetpro.core.data.SessionStore
import com.webrtcmeetpro.core.network.ApiConfig
import com.webrtcmeetpro.core.network.MqApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.context.GlobalContext
import kotlin.coroutines.resume

/** 기본 브라우저에서 구글 로그인을 하고, 서버가 mq://oauth/callback 으로 돌려보내면 MainActivity 가 받는다 */
class AndroidGoogleLogin(private val context: Context) : GoogleLoginLauncher {
    override fun start(serverUrl: String) {
        val uri = Uri.parse(serverUrl.trimEnd('/') + "/api/auth/oauth/app?platform=android")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Firebase 설정(mq.firebase.*) 없이 빌드하면 FirebaseApp 이 없어서 앱 푸시 없이 동작한다 */
class FcmPushDevices(private val context: Context, private val api: MqApi, private val config: ApiConfig) : PushDevices {
    override suspend fun register(): Boolean {
        val token = currentToken() ?: return false
        api.registerPushDevice(token, config.language())
        return true
    }

    override suspend fun unregister() {
        val token = currentToken() ?: return
        api.unregisterPushDevice(token)
    }

    private suspend fun currentToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                cont.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }
}

/** 앱이 꺼져 있어도 FCM 이 이 서비스를 깨워서 알림을 띄운다. 알림 모양을 직접 정하려고 서버는 data 메시지만 보낸다 */
class MqMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 토큰이 바뀌면(앱 데이터 삭제·재설치 등) 로그인한 상태일 때만 다시 등록한다 */
    override fun onNewToken(token: String) {
        val koin = GlobalContext.getOrNull() ?: return
        scope.launch {
            if (koin.get<SessionStore>().load() == null) return@launch
            runCatching { koin.get<PushDevices>().register() }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: return
        val koin = GlobalContext.getOrNull() ?: return
        koin.get<SystemNotifier>().show(title, data["body"].orEmpty(), data["link"], data["tag"])
    }
}
