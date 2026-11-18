package com.webrtcmeetpro.platform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webrtcmeetpro.ui.MqMark
import java.awt.Frame
import java.awt.event.WindowStateListener

private enum class CaptionButton { MINIMIZE, MAXIMIZE, RESTORE, CLOSE }

/**
 * 앱이 직접 그리는 타이틀 바: 왼쪽 MQ 로고, 오른쪽 최소화·최대화·닫기. 색은 왼쪽 메뉴(레일)와 같아서 한 덩어리로 보인다.
 * 버튼을 뺀 나머지는 WindowFrame 이 Windows 제목 표시줄로 알려 줘서 끌기·스냅·두 번 눌러 최대화가 된다.
 */
@Composable
fun WindowTitleBar(window: ComposeWindow, onClose: () -> Unit) {
    var maximized by remember { mutableStateOf(window.extendedState and Frame.MAXIMIZED_BOTH != 0) }
    DisposableEffect(window) {
        val listener = WindowStateListener { maximized = it.newState and Frame.MAXIMIZED_BOTH != 0 }
        window.addWindowStateListener(listener)
        onDispose { window.removeWindowStateListener(listener) }
    }
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(colors.surfaceContainerLow)
            .onSizeChanged {
                WindowFrame.titleBarHeight = it.height
                WindowFrame.attachChildren()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(14.dp))
        Icon(MqMark, contentDescription = null, tint = Color(0xFF7C1FFF), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("MQ", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = colors.onSurface)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxHeight().onSizeChanged { WindowFrame.buttonsWidth = it.width }) {
            CaptionButton(CaptionButton.MINIMIZE) { WindowFrame.minimize(window) }
            CaptionButton(if (maximized) CaptionButton.RESTORE else CaptionButton.MAXIMIZE) { WindowFrame.toggleMaximize(window) }
            CaptionButton(CaptionButton.CLOSE, onClose)
        }
    }
}

@Composable
private fun CaptionButton(kind: CaptionButton, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val colors = MaterialTheme.colorScheme
    val close = kind == CaptionButton.CLOSE
    // Windows 11 과 같은 모양: 닫기만 빨간 배경
    val background = when {
        close && (hovered || pressed) -> Color(0xFFC42B1C).copy(alpha = if (pressed) 0.85f else 1f)
        pressed -> colors.onSurface.copy(alpha = 0.12f)
        hovered -> colors.onSurface.copy(alpha = 0.07f)
        else -> Color.Transparent
    }
    val tint = if (close && (hovered || pressed)) Color.White else colors.onSurface
    Box(
        Modifier.width(46.dp).fillMaxHeight().background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) { drawGlyph(kind, tint) }
    }
}

private fun DrawScope.drawGlyph(kind: CaptionButton, color: Color) {
    val stroke = 1.dp.toPx()
    val s = size.width
    when (kind) {
        CaptionButton.MINIMIZE -> drawLine(color, Offset(0f, s / 2), Offset(s, s / 2), stroke)
        CaptionButton.MAXIMIZE -> drawRect(color, style = Stroke(stroke))
        CaptionButton.RESTORE -> {
            val gap = s * 0.2f
            drawRect(color, topLeft = Offset(0f, gap), size = Size(s - gap, s - gap), style = Stroke(stroke))
            drawLine(color, Offset(gap, 0f), Offset(s, 0f), stroke)
            drawLine(color, Offset(s, 0f), Offset(s, s - gap), stroke)
        }
        CaptionButton.CLOSE -> {
            drawLine(color, Offset(0f, 0f), Offset(s, s), stroke)
            drawLine(color, Offset(s, 0f), Offset(0f, s), stroke)
        }
    }
}
