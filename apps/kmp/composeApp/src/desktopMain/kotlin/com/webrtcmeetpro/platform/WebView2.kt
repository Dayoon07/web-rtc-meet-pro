package com.webrtcmeetpro.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.W32APIOptions
import java.awt.Canvas
import java.awt.Color
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Windows 에 이미 깔린 Edge WebView2 를 MQ 창 안에 붙인다. 크롬 엔진을 통째로 넣으면 설치 파일이 150MB 넘게 커져서
 * 있는 엔진을 빌려 쓴다. 다리 역할은 webview 0.11.0 의 webview.dll (MIT, resources/webview/LICENSE.md).
 */
internal object WebView2 {
    interface Lib : Library {
        fun webview_create(debug: Int, window: Pointer?): Pointer?
        fun webview_destroy(w: Pointer)
        fun webview_run(w: Pointer)
        fun webview_terminate(w: Pointer)
        fun webview_get_window(w: Pointer): Pointer?
        fun webview_dispatch(w: Pointer, fn: DispatchFn, arg: Pointer?)
        fun webview_navigate(w: Pointer, url: String)
        fun webview_init(w: Pointer, js: String)
        fun webview_bind(w: Pointer, name: String, fn: BindFn, arg: Pointer?)
        fun webview_return(w: Pointer, seq: Pointer, status: Int, result: String)
    }

    interface DispatchFn : Callback {
        fun invoke(w: Pointer?, arg: Pointer?)
    }

    interface BindFn : Callback {
        fun invoke(seq: Pointer, req: String?, arg: Pointer?)
    }

    interface CbtProc : WinUser.HOOKPROC {
        fun callback(code: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    }

    /** Windows 64비트이고 WebView2 런타임이 깔려 있을 때만 (Windows 11 은 기본, 10 은 대부분 업데이트로 들어 있다) */
    val available: Boolean by lazy {
        System.getProperty("os.name").orEmpty().startsWith("Windows") && Native.POINTER_SIZE == 8 && runtimeInstalled()
    }

    val lib: Lib? by lazy {
        runCatching { Native.load(extract().absolutePath, Lib::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8")) }.getOrNull()
    }

    /**
     * 회의 화면 기록(쿠키·권한 허락)은 앱 데이터 폴더에 따로 둔다. 다른 사람이 말하면 누르지 않아도 소리가 나오게 자동 재생을 연다.
     * WebView2 는 이 환경 변수를 엔진을 띄울 때 읽는다.
     */
    fun prepareEnvironment() {
        val kernel = Native.load("kernel32", Kernel32Env::class.java, W32APIOptions.UNICODE_OPTIONS)
        kernel.SetEnvironmentVariableW("WEBVIEW2_USER_DATA_FOLDER", File(appDataDir(), "webview").absolutePath)
        kernel.SetEnvironmentVariableW("WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS", "--autoplay-policy=no-user-gesture-required")
    }

    private interface Kernel32Env : Library {
        fun SetEnvironmentVariableW(name: String, value: String?): Boolean
    }

    private fun runtimeInstalled(): Boolean {
        val client = "Microsoft\\EdgeUpdate\\Clients\\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}"
        return listOf(
            WinReg.HKEY_LOCAL_MACHINE to "SOFTWARE\\WOW6432Node\\$client",
            WinReg.HKEY_LOCAL_MACHINE to "SOFTWARE\\$client",
            WinReg.HKEY_CURRENT_USER to "Software\\$client",
        ).any { (root, key) ->
            runCatching { Advapi32Util.registryGetStringValue(root, key, "pv") }.getOrNull()
                ?.let { it.isNotBlank() && it != "0.0.0.0" } == true
        }
    }

    /** jar 안의 dll 은 바로 못 읽어서 앱 데이터 폴더에 꺼내 둔다 (다른 MQ 창이 쓰는 중이면 그대로 쓴다) */
    private fun extract(): File {
        val bytes = WebView2::class.java.getResourceAsStream("/webview/webview.dll")!!.use { it.readBytes() }
        val file = File(appDataDir(), "native/webview-0.11.0.dll")
        if (!file.isFile || file.length() != bytes.size.toLong()) {
            file.parentFile.mkdirs()
            file.writeBytes(bytes)
        }
        return file
    }
}

/**
 * 회의 웹 화면을 그리는 AWT 칸. 웹뷰는 자기 스레드에서 Windows 메시지 루프를 돌린다.
 *
 * webview.dll 은 원래 자기 창을 따로 띄운다. 그 창을 만드는 순간(CBT 훅)에 이 칸의 자식 창으로 바꿔
 * 따로 뜬 창이 깜빡이지 않게 하고, 크기가 바뀌면 dll 이 웹뷰 크기를 스스로 맞추게 한다.
 */
internal class WebView2Panel(
    private val url: String,
    private val onPath: (String) -> Unit,
    private val onFailed: () -> Unit,
) : Canvas() {
    @Volatile private var view: Pointer? = null
    @Volatile private var child: HWND? = null
    @Volatile private var parent: HWND? = null
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val lib get() = WebView2.lib

    // JNA 콜백은 GC 되면 dll 이 부를 때 앱이 죽어서 필드로 잡아 둔다
    private val resizeFn = object : WebView2.DispatchFn {
        override fun invoke(w: Pointer?, arg: Pointer?) = fitChild()
    }
    private val quitFn = object : WebView2.DispatchFn {
        override fun invoke(w: Pointer?, arg: Pointer?) {
            w?.let { lib?.webview_terminate(it) }
        }
    }
    private val pathFn = object : WebView2.BindFn {
        override fun invoke(seq: Pointer, req: String?, arg: Pointer?) {
            view?.let { lib?.webview_return(it, seq, 0, "null") }
            // req 는 인자 배열 JSON: ["/meet/12"]
            PATH_ARG.find(req.orEmpty())?.groupValues?.get(1)?.let(onPath)
        }
    }

    init {
        background = Color.BLACK
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                view?.let { lib?.webview_dispatch(it, resizeFn, null) }
            }
        })
    }

    override fun addNotify() {
        super.addNotify()
        if (!started.compareAndSet(false, true)) return
        // 웹뷰 스레드에서 AWT 를 건드리면 서로 기다리다 멈출 수 있어 창 핸들은 여기서 한 번만 얻는다
        val parent = HWND(Native.getComponentPointer(this)).also { this.parent = it }
        thread(name = "webview2", isDaemon = true) { run(parent) }
    }

    override fun removeNotify() {
        close()
        super.removeNotify()
    }

    /** 회의 화면을 떠날 때. 웹뷰를 닫으면 카메라·마이크도 놓는다 */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        view?.let { lib?.webview_dispatch(it, quitFn, null) }
    }

    private fun run(parent: HWND) {
        val lib = lib
        if (lib == null) {
            onFailed()
            return
        }
        WebView2.prepareEnvironment()
        val w = createAsChild(lib, parent)
        if (w == null) {
            onFailed()
            return
        }
        view = w
        child = lib.webview_get_window(w)?.let(::HWND)
        // 만들어지는 동안 dll 이 창 모양을 바꿨을 수 있어 테두리 없는 자식 창으로 다시 맞춘다
        child?.let {
            User32.INSTANCE.SetWindowLong(it, WinUser.GWL_STYLE, CHILD_STYLE)
            User32.INSTANCE.SetWindowLong(it, WinUser.GWL_EXSTYLE, WS_EX_NOPARENTNOTIFY)
        }
        fitChild()
        lib.webview_bind(w, "mqPath", pathFn, null)
        lib.webview_init(w, PATH_SCRIPT)
        lib.webview_navigate(w, url)
        // 만드는 사이에 회의 화면을 이미 떠났으면 바로 끝낸다
        if (closed.get()) lib.webview_terminate(w)
        lib.webview_run(w)
        view = null
        child = null
        lib.webview_destroy(w)
    }

    /** dll 이 만드는 창(클래스 이름 "webview")을 처음부터 이 칸 안의 자식 창으로 만든다 */
    private fun createAsChild(lib: WebView2.Lib, parent: HWND): Pointer? {
        val size = clientSize(parent)
        val hook = object : WebView2.CbtProc {
            override fun callback(code: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
                if (code == HCBT_CREATEWND) {
                    val cs = Pointer(lParam.toLong()).getPointer(0)
                    val owner = cs.getPointer(CS_PARENT)
                    val className = cs.getPointer(CS_CLASS)
                    // 클래스 이름 대신 숫자(atom)가 오면 0x10000 보다 작다
                    if (owner == null && className != null && Pointer.nativeValue(className) >= 0x10000 &&
                        className.getWideString(0) == "webview"
                    ) {
                        cs.setPointer(CS_PARENT, parent.pointer)
                        cs.setInt(CS_CY, size.second)
                        cs.setInt(CS_CX, size.first)
                        cs.setInt(CS_Y, 0)
                        cs.setInt(CS_X, 0)
                        cs.setInt(CS_STYLE, CHILD_STYLE)
                        cs.setInt(CS_EXSTYLE, WS_EX_NOPARENTNOTIFY)
                    }
                }
                return User32.INSTANCE.CallNextHookEx(null, code, wParam, lParam)
            }
        }
        val handle = User32.INSTANCE.SetWindowsHookEx(WH_CBT, hook, null, Kernel32.INSTANCE.GetCurrentThreadId())
        return try {
            lib.webview_create(0, null)
        } finally {
            handle?.let { User32.INSTANCE.UnhookWindowsHookEx(it) }
            // 훅 콜백이 끝날 때까지 GC 되지 않게
            hook.hashCode()
        }
    }

    /** 칸의 실제 픽셀 크기(배율 적용 후)에 웹뷰 창을 맞춘다. dll 이 WM_SIZE 를 받아 웹뷰 크기를 따라 바꾼다 */
    private fun fitChild() {
        val parent = parent ?: return
        val target = child ?: return
        val (width, height) = clientSize(parent)
        User32.INSTANCE.SetWindowPos(target, null, 0, 0, width, height, SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED or SWP_SHOWWINDOW)
    }

    private fun clientSize(hwnd: HWND): Pair<Int, Int> {
        val rect = RECT()
        User32.INSTANCE.GetClientRect(hwnd, rect)
        return (rect.right - rect.left).coerceAtLeast(1) to (rect.bottom - rect.top).coerceAtLeast(1)
    }

    private companion object {
        const val WH_CBT = 5
        const val HCBT_CREATEWND = 3

        // CREATESTRUCTW (64비트) 안의 위치
        const val CS_PARENT = 24L
        const val CS_CY = 32L
        const val CS_CX = 36L
        const val CS_Y = 40L
        const val CS_X = 44L
        const val CS_STYLE = 48L
        const val CS_CLASS = 64L
        const val CS_EXSTYLE = 72L

        const val CHILD_STYLE = 0x40000000 or 0x10000000 or 0x02000000 or 0x04000000 // CHILD | VISIBLE | CLIPCHILDREN | CLIPSIBLINGS
        const val WS_EX_NOPARENTNOTIFY = 0x00000004
        const val SWP_NOZORDER = 0x0004
        const val SWP_NOACTIVATE = 0x0010
        const val SWP_FRAMECHANGED = 0x0020
        const val SWP_SHOWWINDOW = 0x0040

        val PATH_ARG = Regex("""^\[\s*"([^"]*)"""")

        /** 웹은 화면 이동을 history API 로 해서, 주소가 바뀔 때마다 앱에 알려 준다 */
        val PATH_SCRIPT = """
            (() => {
              const report = () => { try { window.mqPath && window.mqPath(location.pathname); } catch (e) {} };
              for (const k of ['pushState', 'replaceState']) {
                const original = history[k];
                history[k] = function () { const r = original.apply(this, arguments); report(); return r; };
              }
              addEventListener('popstate', report);
              addEventListener('DOMContentLoaded', report);
            })();
        """.trimIndent()
    }
}
