package com.webrtcmeetpro.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * MQ 는 한 번에 하나만 뜬다. 창을 닫아도 트레이에 남는 앱이라, 막지 않으면 실행할 때마다 하나씩 쌓이고
 * 쌓인 MQ 들이 파일을 쥐고 있어서 업데이트 설치가 파일을 바꾸지 못한다.
 *
 *   - 이미 같은 버전이 떠 있으면: 그 창을 앞으로 띄우고 이번 실행은 끝낸다
 *   - 다른 버전(업데이트 전 것)이 떠 있으면: 그쪽을 끝내고 이번 실행이 이어서 뜬다
 *
 * 누가 주인인지는 Windows 이름 있는 뮤텍스(Named Mutex)로 정한다. 파일이 아니라 Windows 가 이 로그인 세션 안에서
 * 관리하는 이름이라, %APPDATA% 를 따로 보는 프로그램(예: Claude 앱)이 MQ 를 띄워도 같은 것을 본다.
 * 주인이 비정상으로 꺼지면 Windows 가 뮤텍스를 없애 준다.
 * 주인은 이 PC 안 포트로 요청을 받고, 그 포트 번호는 이름 있는 공유 메모리에 적어 둔다 (같은 이유로 파일에 적지 않는다).
 */
object SingleInstance {
    private val version = System.getProperty("jpackage.app-version") ?: "dev"

    /** 다른 실행이 "창 띄워 줘" 라고 하면 부른다 (main 이 창을 보이게 한다) */
    @Volatile var onShow: () -> Unit = {}

    // 앱이 끝날 때까지 쥐고 있는다 (놓으면 Windows 가 이름을 없앤다)
    private var mutex: WinNT.HANDLE? = null
    private var portMap: WinNT.HANDLE? = null
    private var portView: Pointer? = null

    /** @return false 면 이미 떠 있는 MQ 에 창을 맡겼으니 이번 실행은 바로 끝내야 한다 */
    fun acquire(): Boolean {
        if (!WindowFrame.supported) return true
        val kernel = Kernel32.INSTANCE
        // 예전 버전이 끝나기를 최대 10초 기다린다.
        // 주인이 막 켜지는 중이면 포트가 아직 안 적혀 있어서 물어봐도 답이 없으니, 기다리는 동안 계속 다시 묻는다
        repeat(50) {
            // 뮤텍스를 못 만들면 확인할 길이 없으니 그냥 띄운다
            val handle = kernel.CreateMutex(null, false, MUTEX_NAME) ?: return true
            // JVM 이 중간에 Windows 오류 값을 덮어쓸 수 있어서, JNA 가 호출 직후 저장해 둔 값을 읽는다
            if (Native.getLastError() != WinError.ERROR_ALREADY_EXISTS) {
                mutex = handle
                listen()
                return true
            }
            kernel.CloseHandle(handle)
            // SHOW 면 주인이 창을 띄웠으니 끝, BYE 면 주인(예전 버전)이 끝나는 중이니 뮤텍스가 없어지기를 기다린다
            if (ask() == SHOW) return false
            Thread.sleep(200)
        }
        // 주인이 응답도 없고 끝나지도 않으면 이번 실행은 접는다. 하나 더 띄우면 다시 파일이 잠겨 업데이트가 막힌다
        return false
    }

    private fun ask(): String? = runCatching {
        val port = readPort() ?: return null
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1000)
            socket.soTimeout = 2000
            socket.getOutputStream().write("$HELLO $version\n".toByteArray())
            socket.getOutputStream().flush()
            socket.getInputStream().bufferedReader().readLine()
        }
    }.getOrNull()

    private fun listen() {
        val server = runCatching { ServerSocket(0, 5, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        writePort(server.localPort)
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

    private fun writePort(port: Int) {
        val kernel = Kernel32.INSTANCE
        val map = kernel.CreateFileMapping(WinBase.INVALID_HANDLE_VALUE, null, WinNT.PAGE_READWRITE, 0, PORT_BYTES, PORT_MAP_NAME) ?: return
        val view = kernel.MapViewOfFile(map, WinNT.FILE_MAP_ALL_ACCESS, 0, 0, PORT_BYTES)
        if (view == null) {
            kernel.CloseHandle(map)
            return
        }
        view.setInt(0, port)
        portMap = map
        portView = view
    }

    /** 주인이 적어 둔 포트. 아직 안 적혔으면 null */
    private fun readPort(): Int? {
        val kernel = Kernel32.INSTANCE
        val map = kernel.CreateFileMapping(WinBase.INVALID_HANDLE_VALUE, null, WinNT.PAGE_READWRITE, 0, PORT_BYTES, PORT_MAP_NAME) ?: return null
        try {
            val view = kernel.MapViewOfFile(map, WinNT.FILE_MAP_READ, 0, 0, PORT_BYTES) ?: return null
            try {
                return view.getInt(0).takeIf { it in 1..65535 }
            } finally {
                kernel.UnmapViewOfFile(view)
            }
        } finally {
            kernel.CloseHandle(map)
        }
    }

    // 설치 파일의 업그레이드 UUID 를 붙여서 다른 프로그램의 이름과 겹치지 않게 한다. Local\ 은 이 Windows 로그인 세션 안에서만
    private const val MUTEX_NAME = "Local\\MQ-6c1f5f0e-8f0b-4a53-9a3e-6f2a3c7e9d41-instance"
    private const val PORT_MAP_NAME = "Local\\MQ-6c1f5f0e-8f0b-4a53-9a3e-6f2a3c7e9d41-port"
    private const val PORT_BYTES = 4

    private const val HELLO = "mq-hello"
    private const val SHOW = "show"
    private const val BYE = "bye"
}
