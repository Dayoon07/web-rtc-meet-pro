package com.webrtcmeetpro.feature.meetings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.model.MeetingNotes
import com.webrtcmeetpro.platform.formatDateTime
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.notes_actions
import com.webrtcmeetpro.resources.notes_decisions
import com.webrtcmeetpro.resources.notes_disclaimer
import com.webrtcmeetpro.resources.notes_no_actions
import com.webrtcmeetpro.resources.notes_no_owner
import com.webrtcmeetpro.resources.notes_regenerate
import com.webrtcmeetpro.resources.notes_status_empty
import com.webrtcmeetpro.resources.notes_status_failed
import com.webrtcmeetpro.resources.notes_status_live
import com.webrtcmeetpro.resources.notes_status_processing
import com.webrtcmeetpro.resources.notes_title
import org.jetbrains.compose.resources.stringResource

/** 회의 상세의 AI 회의록: 요약·결정 사항·할 일(담당자·기한). 웹 MeetingNotesCard 와 같은 내용 */
@Composable
fun MeetingNotesSection(notes: MeetingNotes, regenerating: Boolean, onRegenerate: () -> Unit) {
    LimeetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Outlined.EventNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.notes_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (notes.canRegenerate) {
                    TextButton(onClick = onRegenerate, enabled = !regenerating) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" " + stringResource(Res.string.notes_regenerate))
                    }
                }
            }
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            when (notes.status) {
                "RECORDING", "PAUSED" -> Text(stringResource(Res.string.notes_status_live), style = MaterialTheme.typography.bodyMedium, color = muted)
                "PROCESSING" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.notes_status_processing), style = MaterialTheme.typography.bodyMedium, color = muted)
                }
                "EMPTY" -> Text(stringResource(Res.string.notes_status_empty), style = MaterialTheme.typography.bodyMedium, color = muted)
                "FAILED" -> Text(stringResource(Res.string.notes_status_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                "READY" -> {
                    notes.generatedAt?.let { Text(formatDateTime(it), style = MaterialTheme.typography.labelSmall, color = muted) }
                    notes.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    notes.summary.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    if (notes.decisions.isNotEmpty()) {
                        Text(stringResource(Res.string.notes_decisions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        notes.decisions.forEach { d ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x1A16A34A)).padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                                Text(d, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Text(stringResource(Res.string.notes_actions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (notes.actionItems.isEmpty()) {
                        Text(stringResource(Res.string.notes_no_actions), style = MaterialTheme.typography.bodyMedium, color = muted)
                    }
                    notes.actionItems.forEach { a ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(a.task, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(a.owner ?: stringResource(Res.string.notes_no_owner), a.due).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Text(stringResource(Res.string.notes_disclaimer), style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}
