package com.webrtcmeetpro.platform

import java.awt.Desktop
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import kotlin.concurrent.thread

/**
 * 기본 브라우저에서 구글 로그인을 하고, 서버가 http://127.0.0.1:<포트>/oauth/callback 으로 돌려보낸 결과를 받는다.
 * 데스크톱 앱은 mq:// 같은 링크를 받을 수 없어서 이 PC 안에서만 열리는 임시 포트로 한 번만 받는다.
 */
class DesktopGoogleLogin : GoogleLoginLauncher {
    @Volatile private var server: ServerSocket? = null

    override fun start(serverUrl: String) {
        // 다시 누르면 이전 대기를 끝내고 새로 시작한다
        server?.runCatching { close() }
        val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply { soTimeout = TIMEOUT_MS }
        server = socket
        thread(name = "google-login-callback", isDaemon = true) { receive(socket) }
        Desktop.getDesktop().browse(URI(serverUrl.trimEnd('/') + "/api/auth/oauth/app?platform=desktop&port=${socket.localPort}"))
    }

    private fun receive(socket: ServerSocket) {
        try {
            socket.accept().use { client ->
                // 예: GET /oauth/callback?code=abc HTTP/1.1
                val requestLine = client.getInputStream().bufferedReader().readLine().orEmpty()
                val path = requestLine.split(' ').getOrNull(1).orEmpty()
                val ok = "code=" in path
                val html = """
                    <!doctype html><html><head><meta charset="utf-8"><title>MQ</title></head>
                    <body style="font-family:sans-serif;text-align:center;padding-top:80px">
                    <h2>${if (ok) "로그인했어요 · Signed in" else "로그인하지 못했어요 · Sign-in failed"}</h2>
                    <p>이 창을 닫고 MQ 앱으로 돌아가세요. · You can close this tab and return to the MQ app.</p>
                    </body></html>
                """.trimIndent().toByteArray()
                client.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(html)
                    flush()
                }
                if (path.startsWith("/oauth/callback")) OAuthCallbacks.deliver(path)
            }
        } catch (_: SocketTimeoutException) {
            // 5분 동안 돌아오지 않으면 포트를 닫는다 (버튼을 다시 누르면 된다)
        } catch (_: Exception) {
            // 다시 눌러서 이전 대기를 닫은 경우
        } finally {
            socket.runCatching { close() }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5 * 60_000
    }
}
