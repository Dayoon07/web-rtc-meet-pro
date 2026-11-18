package com.webrtcmeetpro.platform

import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * MQ 는 한 번에 하나만 뜬다. 창을 닫아도 트레이에 남는 앱이라, 막지 않으면 실행할 때마다 하나씩 쌓이고
 * 쌓인 MQ 들이 파일을 쥐고 있어서 업데이트 설치가 파일을 바꾸지 못한다.
 *
 *   - 이미 같은 버전이 떠 있으면: 그 창을 앞으로 띄우고 이번 실행은 끝낸다
 *   - 다른 버전(업데이트 전 것)이 떠 있으면: 그쪽을 끝내고 이번 실행이 이어서 뜬다
 *
 * %APPDATA%\MQ\instance.lock 을 잠근 쪽이 주인이고, 주인은 instance.port 에 적은 이 PC 안 포트로 요청을 받는다.
 */
object SingleInstance {
    private val version = System.getProperty("jpackage.app-version") ?: "dev"

    /** 다른 실행이 "창 띄워 줘" 라고 하면 부른다 (main 이 창을 보이게 한다) */
    @Volatile var onShow: () -> Unit = {}

    // 앱이 끝날 때까지 잠금을 쥐고 있는다
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /** @return false 면 이미 떠 있는 MQ 에 창을 맡겼으니 이번 실행은 바로 끝내야 한다 */
    fun acquire(): Boolean {
        val dir = appDataDir()
        val lockFile = File(dir, "instance.lock").toPath()
        val portFile = File(dir, "instance.port")
        var asked = false
        // 예전 버전이 끝나기를 최대 10초 기다린다
        repeat(50) {
            val ch = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val l = runCatching { ch.tryLock() }.getOrNull()
            if (l != null) {
                channel = ch
                lock = l
                listen(portFile)
                return true
            }
            ch.close()
            if (!asked) {
                asked = true
                if (ask(portFile) == SHOW) return false
            }
            Thread.sleep(200)
        }
        // 주인이 응답도 없고 끝나지도 않으면, 아무것도 안 뜨는 것보다는 하나 더 띄운다
        return true
    }

    private fun ask(portFile: File): String? = runCatching {
        val port = portFile.readText().trim().toInt()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1000)
            socket.soTimeout = 2000
            socket.getOutputStream().write("$HELLO $version\n".toByteArray())
            socket.getOutputStream().flush()
            socket.getInputStream().bufferedReader().readLine()
        }
    }.getOrNull()

    private fun listen(portFile: File) {
        val server = runCatching { ServerSocket(0, 5, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        runCatching { portFile.writeText(server.localPort.toString()) }
        thread(name = "single-instance", isDaemon = true) {
            while (true) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    client.use {
                        it.soTimeout = 2000
                        val line = it.getInputStream().bufferedReader().readLine().orEmpty()
                        if (!line.startsWith(HELLO)) return@use
                        val other = line.removePrefix(HELLO).trim()
                        val sameVersion = other == version
                        it.getOutputStream().write("${if (sameVersion) SHOW else BYE}\n".toByteArray())
                        it.getOutputStream().flush()
                        if (sameVersion) onShow() else exitProcess(0)
                    }
                }
            }
        }
    }

    private const val HELLO = "mq-hello"
    private const val SHOW = "show"
    private const val BYE = "bye"
}
