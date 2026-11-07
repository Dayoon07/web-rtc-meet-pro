package com.webrtcmeetpro.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.webrtcmeetpro.core.designsystem.component.RemoteImage
import com.webrtcmeetpro.core.model.ChatFile
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.chat_file_open
import com.webrtcmeetpro.ui.resolveImageUrl
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

/**
 * 채팅 첨부파일 (웹 MessageFiles 와 같은 규칙).
 * - 이미지: 미리보기. 누르면 브라우저에서 원본
 * - 그 밖: 이름·크기. 누르면 브라우저가 내려받는다 (download=true)
 * 주소는 서버가 서명해 준 상대 경로라 API 주소를 붙여 쓴다.
 */
@Composable
fun MessageFiles(files: List<ChatFile>, mine: Boolean) {
    val images = files.filter { it.image }
    val others = files.filterNot { it.image }
    Column(
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (images.size == 1) {
            ChatImage(images.single(), Modifier.widthIn(max = 240.dp).heightIn(max = 280.dp), ContentScale.Fit, DpSize(200.dp, 150.dp))
        } else if (images.isNotEmpty()) {
            images.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { ChatImage(it, Modifier.size(96.dp), ContentScale.Crop, DpSize(96.dp, 96.dp)) }
                }
            }
        }
        others.forEach { FileRow(it) }
    }
}

@Composable
private fun ChatImage(file: ChatFile, modifier: Modifier, contentScale: ContentScale, placeholder: DpSize) {
    val uriHandler = LocalUriHandler.current
    val url = resolveImageUrl(file.url)
    RemoteImage(
        url = url,
        contentDescription = file.name,
        contentScale = contentScale,
        placeholderSize = placeholder,
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = url != null) { url?.let { runCatching { uriHandler.openUri(it) } } },
    )
}

@Composable
private fun FileRow(file: ChatFile) {
    val uriHandler = LocalUriHandler.current
    val url = resolveImageUrl(file.url)
    val open = stringResource(Res.string.chat_file_open)
    Row(
        Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = url != null, onClickLabel = open) { url?.let { runCatching { uriHandler.openUri("$it&download=true") } } }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatBytes(file.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.FileDownload, contentDescription = open, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** 파일 크기: 1.2 MB / 340 KB / 12 B */
internal fun formatBytes(bytes: Long): String {
    fun oneDecimal(v: Double): String {
        if (v >= 10) return v.roundToLong().toString()
        val tenths = (v * 10).roundToLong()
        return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
    }
    return when {
        bytes >= 1024L * 1024 -> oneDecimal(bytes / 1024.0 / 1024.0) + " MB"
        bytes >= 1024 -> oneDecimal(bytes / 1024.0) + " KB"
        else -> "$bytes B"
    }
}
