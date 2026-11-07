package com.webrtcmeetpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.webrtcmeetpro.core.network.UnexpectedError
import com.webrtcmeetpro.core.network.UnexpectedErrors
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.error_dialog_cause
import com.webrtcmeetpro.resources.error_dialog_code
import com.webrtcmeetpro.resources.error_dialog_email
import com.webrtcmeetpro.resources.error_dialog_hint
import com.webrtcmeetpro.resources.error_dialog_lead
import com.webrtcmeetpro.resources.error_dialog_mail_subject
import com.webrtcmeetpro.resources.error_dialog_ok
import com.webrtcmeetpro.resources.error_dialog_title
import com.webrtcmeetpro.resources.error_reason_CLIENT
import com.webrtcmeetpro.resources.error_reason_CODE
import com.webrtcmeetpro.resources.error_reason_DB_CONNECTION
import com.webrtcmeetpro.resources.error_reason_DB_CONSTRAINT
import com.webrtcmeetpro.resources.error_reason_DB_ETC
import com.webrtcmeetpro.resources.error_reason_DB_SCHEMA
import com.webrtcmeetpro.resources.error_reason_EXTERNAL
import com.webrtcmeetpro.resources.error_reason_NETWORK
import org.jetbrains.compose.resources.stringResource

/** 문의 이메일 (웹 SUPPORT_EMAIL 과 같은 값) */
const val SUPPORT_EMAIL = "gangd0642@gmail.com"

/**
 * 서버가 에러 코드를 만들어 기록한 500 응답이 오면 띄우는 안내 창 (웹 UnexpectedErrorDialog 와 같은 구성):
 * 사과 + "잠시 후 다시 시도해 주세요." → 원인 → 문의 이메일 → 에러 코드.
 * 앱 루트에 하나만 두고, 이미 떠 있으면 새 에러는 무시한다.
 */
@Composable
fun UnexpectedErrorHost() {
    var current by remember { mutableStateOf<UnexpectedError?>(null) }
    LaunchedEffect(Unit) {
        UnexpectedErrors.events.collect { if (current == null) current = it }
    }
    val error = current ?: return
    val uriHandler = LocalUriHandler.current
    val subject = stringResource(Res.string.error_dialog_mail_subject, error.errorCode)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = { current = null },
        title = { Text(stringResource(Res.string.error_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.error_dialog_lead), style = MaterialTheme.typography.bodyMedium)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                ) {
                    Text(stringResource(Res.string.error_dialog_cause), style = MaterialTheme.typography.labelSmall, color = labelColor)
                    Text(reasonText(error.reason), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.error_dialog_email) + ": ", style = MaterialTheme.typography.bodySmall, color = labelColor)
                    Text(
                        SUPPORT_EMAIL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline,
                        // 메일 앱이 없으면 열리지 않는다 (앱이 죽지 않게)
                        modifier = Modifier.clickable {
                            runCatching { uriHandler.openUri("mailto:$SUPPORT_EMAIL?subject=" + percentEncode(subject)) }
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.error_dialog_code) + ": ", style = MaterialTheme.typography.bodySmall, color = labelColor)
                    SelectionContainer {
                        Text(error.errorCode, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
                Text(stringResource(Res.string.error_dialog_hint), style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
        },
        confirmButton = {
            TextButton(onClick = { current = null }) { Text(stringResource(Res.string.error_dialog_ok)) }
        },
    )
}

/** 서버 원인 분류(admin_error_logs.category) → 쉬운 말 */
@Composable
private fun reasonText(reason: String): String = stringResource(
    when (reason) {
        "DB_SCHEMA" -> Res.string.error_reason_DB_SCHEMA
        "DB_CONSTRAINT" -> Res.string.error_reason_DB_CONSTRAINT
        "DB_CONNECTION" -> Res.string.error_reason_DB_CONNECTION
        "DB_ETC" -> Res.string.error_reason_DB_ETC
        "EXTERNAL" -> Res.string.error_reason_EXTERNAL
        "CLIENT" -> Res.string.error_reason_CLIENT
        "NETWORK" -> Res.string.error_reason_NETWORK
        else -> Res.string.error_reason_CODE
    },
)

private const val HEX = "0123456789ABCDEF"

/** mailto 제목용 퍼센트 인코딩 (UTF-8, 영문·숫자·-._~ 만 그대로) */
private fun percentEncode(text: String): String = buildString {
    text.encodeToByteArray().forEach { byte ->
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') append(ch)
        else append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
    }
}
