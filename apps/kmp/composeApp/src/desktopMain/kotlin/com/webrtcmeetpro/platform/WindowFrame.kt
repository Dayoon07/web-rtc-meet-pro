package com.webrtcmeetpro.platform

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/**
 * 타이틀 바를 앱이 직접 그리게 한다 (Windows). 창은 테두리 없이(undecorated) 만들고, 여기서 Windows 창 스타일을 되돌려
 * 끌어서 옮기기·화면 가장자리 스냅·두 번 눌러 최대화·가장자리로 크기 조절·최소화 애니메이션을 그대로 쓴다.
 * 창 메시지를 가로채서 Windows 가 그리는 타이틀 바 영역(non-client)을 0 으로 만들고, 앱이 그린 타이틀 바 위치를 알려 준다.
 */
object WindowFrame {
    /** 앱이 그린 타이틀 바 높이와 오른쪽 버튼 묶음 너비 (픽셀). 이 안쪽은 Windows 가 "제목 표시줄"로 다룬다 */
    @Volatile var titleBarHeight: Int = 0
    @Volatile var buttonsWidth: Int = 0

    val supported: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows") && Native.POINTER_SIZE == 8

    private var original: Pointer? = null
    private var frame: HWND? = null

    /** 자식 창(Compose 화면을 그리는 캔버스 등)의 원래 창 처리기. 창 핸들 값 → 처리기 */
    private val childOriginals = java.util.concurrent.ConcurrentHashMap<Long, Pointer>()

    // 네이티브가 부르는 콜백이라 GC 되지 않게 잡아 둔다
    private val proc = object : WinUser.WindowProc {
        override fun callback(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT =
            runCatching { handle(hwnd, msg, wParam, lParam) }.getOrNull() ?: callOriginal(hwnd, msg, wParam, lParam)
    }

    /*
     * Compose 화면은 창을 덮는 자식 창에 그려져서, 마우스 위치 판단(WM_NCHITTEST)이 큰 창이 아니라 자식 창으로 간다.
     * 자식 창이 타이틀 바·가장자리 자리에서는 HTTRANSPARENT 로 답해 큰 창에 넘기게 한다 (같은 스레드라 넘어간다).
     */
    private val childProc = object : WinUser.WindowProc {
        override fun callback(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
            val previous = childOriginals[Pointer.nativeValue(hwnd.pointer)]
            if (msg == WM_NCHITTEST) {
                val parent = frame
                val code = parent?.let { runCatching { hitTest(it, lParam) }.getOrNull() }
                if (code != null) return LRESULT(HTTRANSPARENT.toLong())
            }
            return previous?.let { User32.INSTANCE.CallWindowProc(it, hwnd, msg, wParam, lParam) }
                ?: User32.INSTANCE.DefWindowProc(hwnd, msg, wParam, lParam)
        }
    }

    /** 새로 생긴 자식 창까지 붙인다. 화면을 그리는 캔버스는 렌더러가 바뀌면 다시 만들어질 수 있어 여러 번 불러도 된다 */
    fun attachChildren() {
        val parent = frame ?: return
        val user32 = User32.INSTANCE
        val thread = user32.GetWindowThreadProcessId(parent, null)
        val pointer = CallbackReference.getFunctionPointer(childProc)
        val visit = object : WinUser.WNDENUMPROC {
            override fun callback(child: HWND, data: Pointer?): Boolean {
                val key = Pointer.nativeValue(child.pointer)
                // 회의 화면(WebView2)처럼 다른 스레드의 창은 건드리지 않는다
                if (!childOriginals.containsKey(key) && user32.GetWindowThreadProcessId(child, null) == thread) {
                    childOriginals[key] = Pointer(user32.GetWindowLongPtr(child, GWLP_WNDPROC).toLong())
                    user32.SetWindowLongPtr(child, GWLP_WNDPROC, pointer)
                }
                return true
            }
        }
        user32.EnumChildWindows(parent, visit, null)
    }

    fun install(window: ComposeWindow) {
        if (!supported || original != null) return
        val hwnd = HWND(Pointer(window.windowHandle))
        frame = hwnd
        val user32 = User32.INSTANCE
        val style = user32.GetWindowLong(hwnd, WinUser.GWL_STYLE)
        user32.SetWindowLong(hwnd, WinUser.GWL_STYLE, (style and WS_POPUP.inv()) or WS_CAPTION or WS_THICKFRAME or WS_SYSMENU or WS_MINIMIZEBOX or WS_MAXIMIZEBOX)
        original = Pointer(user32.GetWindowLongPtr(hwnd, GWLP_WNDPROC).toLong())
        user32.SetWindowLongPtr(hwnd, GWLP_WNDPROC, CallbackReference.getFunctionPointer(proc))
        // Windows 11 둥근 모서리
        runCatching { Dwm.cornerRound(hwnd) }
        user32.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED)
        attachChildren()
    }

    fun minimize(window: ComposeWindow) = show(window, SW_MINIMIZE)

    fun toggleMaximize(window: ComposeWindow) = show(window, if (isMaximized(HWND(Pointer(window.windowHandle)))) SW_RESTORE else SW_MAXIMIZE)

    private fun show(window: ComposeWindow, command: Int) {
        User32.INSTANCE.ShowWindow(HWND(Pointer(window.windowHandle)), command)
    }

    private fun handle(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT? = when (msg) {
        // 창 전체를 앱 화면으로. 최대화되면 Windows 가 창을 화면 밖으로 테두리만큼 키우므로 그만큼 안쪽으로 줄인다
        WM_NCCALCSIZE -> {
            if (wParam.toLong() != 0L && isMaximized(hwnd)) {
                val rect = Pointer(lParam.toLong())
                val (fx, fy) = frame(hwnd)
                rect.setInt(0, rect.getInt(0) + fx)
                rect.setInt(4, rect.getInt(4) + fy)
                rect.setInt(8, rect.getInt(8) - fx)
                rect.setInt(12, rect.getInt(12) - fy)
            }
            LRESULT(0)
        }
        WM_NCHITTEST -> hitTest(hwnd, lParam)
        // 최대화 크기를 지금 모니터의 작업 영역(작업 표시줄 제외)에 맞춘다. 최소 크기는 AWT 가 넣은 값을 그대로 둔다
        WM_GETMINMAXINFO -> {
            val result = callOriginal(hwnd, msg, wParam, lParam)
            fitMaximized(hwnd, Pointer(lParam.toLong()))
            result
        }
        // 활성/비활성이 바뀔 때 Windows 가 예전 타이틀 바를 잠깐 다시 그리지 않게
        WM_NCACTIVATE -> callOriginal(hwnd, msg, wParam, LPARAM(-1))
        else -> null
    }

    private fun hitTest(hwnd: HWND, lParam: LPARAM): LRESULT? {
        val point = Memory(8).apply {
            setInt(0, (lParam.toLong() and 0xFFFF).toShort().toInt())
            setInt(4, ((lParam.toLong() shr 16) and 0xFFFF).toShort().toInt())
        }
        Extra.lib.ScreenToClient(hwnd, point)
        val x = point.getInt(0)
        val y = point.getInt(4)
        val client = RECT().also { User32.INSTANCE.GetClientRect(hwnd, it) }
        val width = client.right - client.left
        val height = client.bottom - client.top
        if (!isMaximized(hwnd)) {
            // 가장자리 몇 픽셀은 크기 조절 손잡이
            val border = scaled(hwnd, RESIZE_BORDER)
            val left = x < border
            val right = x >= width - border
            val top = y < border
            val bottom = y >= height - border
            val code = when {
                top && left -> HTTOPLEFT
                top && right -> HTTOPRIGHT
                bottom && left -> HTBOTTOMLEFT
                bottom && right -> HTBOTTOMRIGHT
                left -> HTLEFT
                right -> HTRIGHT
                top -> HTTOP
                bottom -> HTBOTTOM
                else -> null
            }
            if (code != null) return LRESULT(code.toLong())
        }
        // 앱 타이틀 바(버튼 제외)는 제목 표시줄: 끌기·스냅·두 번 눌러 최대화·오른쪽 클릭 메뉴
        if (y in 0 until titleBarHeight && x < width - buttonsWidth) return LRESULT(HTCAPTION.toLong())
        return null
    }

    private fun fitMaximized(hwnd: HWND, info: Pointer) {
        val monitor = Extra.lib.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST) ?: return
        val mi = Memory(40).apply { clear(); setInt(0, 40) }
        if (!Extra.lib.GetMonitorInfoW(monitor, mi)) return
        val (fx, fy) = frame(hwnd)
        val monLeft = mi.getInt(4)
        val monTop = mi.getInt(8)
        val workLeft = mi.getInt(20)
        val workTop = mi.getInt(24)
        val workRight = mi.getInt(28)
        val workBottom = mi.getInt(32)
        // MINMAXINFO: ptMaxSize(8), ptMaxPosition(16)
        info.setInt(8, workRight - workLeft + 2 * fx)
        info.setInt(12, workBottom - workTop + 2 * fy)
        info.setInt(16, workLeft - monLeft - fx)
        info.setInt(20, workTop - monTop - fy)
    }

    private fun isMaximized(hwnd: HWND) = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE) and WS_MAXIMIZE != 0

    /** 최대화됐을 때 화면 밖으로 나가는 테두리 두께 (모니터 배율 반영) */
    private fun frame(hwnd: HWND): Pair<Int, Int> {
        val dpi = dpi(hwnd)
        val padded = Extra.lib.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)
        return (Extra.lib.GetSystemMetricsForDpi(SM_CXFRAME, dpi) + padded) to (Extra.lib.GetSystemMetricsForDpi(SM_CYFRAME, dpi) + padded)
    }

    private fun scaled(hwnd: HWND, px: Int) = px * dpi(hwnd) / 96

    private fun dpi(hwnd: HWND) = Extra.lib.GetDpiForWindow(hwnd).takeIf { it > 0 } ?: 96

    private fun callOriginal(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT =
        original?.let { User32.INSTANCE.CallWindowProc(it, hwnd, msg, wParam, lParam) }
            ?: User32.INSTANCE.DefWindowProc(hwnd, msg, wParam, lParam)

    private object Extra {
        interface Lib : StdCallLibrary {
            fun ScreenToClient(hwnd: HWND, point: Pointer): Boolean
            fun MonitorFromWindow(hwnd: HWND, flags: Int): Pointer?
            fun GetMonitorInfoW(monitor: Pointer, info: Pointer): Boolean
            fun GetDpiForWindow(hwnd: HWND): Int
            fun GetSystemMetricsForDpi(index: Int, dpi: Int): Int
        }

        val lib: Lib = Native.load("user32", Lib::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }

    private object Dwm {
        interface Lib : StdCallLibrary {
            fun DwmSetWindowAttribute(hwnd: HWND, attribute: Int, value: Pointer, size: Int): Int
        }

        private val lib: Lib by lazy { Native.load("dwmapi", Lib::class.java) }

        fun cornerRound(hwnd: HWND) {
            // DWMWA_WINDOW_CORNER_PREFERENCE = 33, DWMWCP_ROUND = 2
            lib.DwmSetWindowAttribute(hwnd, 33, Memory(4).apply { setInt(0, 2) }, 4)
        }
    }

    private const val GWLP_WNDPROC = -4
    private const val WM_GETMINMAXINFO = 0x0024
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val WM_NCACTIVATE = 0x0086

    private const val HTTRANSPARENT = -1
    private const val HTCAPTION = 2
    private const val HTLEFT = 10
    private const val HTRIGHT = 11
    private const val HTTOP = 12
    private const val HTTOPLEFT = 13
    private const val HTTOPRIGHT = 14
    private const val HTBOTTOM = 15
    private const val HTBOTTOMLEFT = 16
    private const val HTBOTTOMRIGHT = 17

    private const val WS_POPUP = 0x80000000.toInt()
    private const val WS_CAPTION = 0x00C00000
    private const val WS_SYSMENU = 0x00080000
    private const val WS_THICKFRAME = 0x00040000
    private const val WS_MINIMIZEBOX = 0x00020000
    private const val WS_MAXIMIZEBOX = 0x00010000
    private const val WS_MAXIMIZE = 0x01000000

    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020

    private const val SW_MAXIMIZE = 3
    private const val SW_MINIMIZE = 6
    private const val SW_RESTORE = 9

    private const val SM_CXFRAME = 32
    private const val SM_CYFRAME = 33
    private const val SM_CXPADDEDBORDER = 92
    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val RESIZE_BORDER = 6
}
