package com.webrtcmeetpro.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** main.kt 가 창을 만들 때 넣어 둔다 */
object DesktopWindow {
    @Volatile var window: ComposeWindow? = null

    /** 작업 표시줄·Alt+Tab 용 MQ 아이콘. 크기별로 넣어야 작게 줄어도 뭉개지지 않는다 */
    val icons: List<BufferedImage> by lazy {
        listOf(16, 24, 32, 48, 64, 256).mapNotNull { size ->
            DesktopWindow::class.java.getResourceAsStream("/icons/mq-$size.png")?.use(ImageIO::read)
        }
    }
}

@Composable
actual fun WindowChromeColors(caption: Color, text: Color, border: Color, dark: Boolean) {
    LaunchedEffect(caption, text, border, dark) {
        val window = DesktopWindow.window ?: return@LaunchedEffect
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return@LaunchedEffect
        runCatching { Dwm.apply(window, caption, text, border, dark) }
    }
}

/**
 * DwmSetWindowAttribute 로 Windows 기본 타이틀 바의 색만 바꾼다. 직접 그리는 타이틀 바와 달리
 * 창 끌기·스냅·최대화 같은 Windows 동작이 그대로 남는다.
 * 색 지정은 Windows 11 부터, Windows 10 에서는 다크 모드만 먹고 나머지는 무시된다.
 */
private object Dwm {
    private interface DwmApi : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: HWND, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val api by lazy { Native.load("dwmapi", DwmApi::class.java) }

    private const val USE_IMMERSIVE_DARK_MODE = 20
    private const val BORDER_COLOR = 34
    private const val CAPTION_COLOR = 35
    private const val TEXT_COLOR = 36

    fun apply(window: ComposeWindow, caption: Color, text: Color, border: Color, dark: Boolean) {
        val hwnd = HWND(Pointer(window.windowHandle))
        set(hwnd, USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
        set(hwnd, CAPTION_COLOR, caption.colorRef())
        set(hwnd, TEXT_COLOR, text.colorRef())
        set(hwnd, BORDER_COLOR, border.colorRef())
    }

    private fun set(hwnd: HWND, attribute: Int, value: Int) {
        api.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), 4)
    }

    /** Win32 COLORREF 는 0x00BBGGRR */
    private fun Color.colorRef(): Int {
        val argb = toArgb()
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }
}
