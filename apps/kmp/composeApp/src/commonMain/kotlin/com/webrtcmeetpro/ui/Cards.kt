package com.webrtcmeetpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webrtcmeetpro.core.designsystem.component.Avatar
import com.webrtcmeetpro.core.designsystem.component.Badge
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.designsystem.theme.Live
import com.webrtcmeetpro.core.model.Meeting
import com.webrtcmeetpro.core.model.MeetingStatus
import com.webrtcmeetpro.core.model.TeamRole
import com.webrtcmeetpro.core.model.TeamSummary
import com.webrtcmeetpro.core.network.ApiConfig
import com.webrtcmeetpro.platform.formatDateTime
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.meeting_duration
import com.webrtcmeetpro.resources.meeting_host
import com.webrtcmeetpro.resources.meeting_live_count
import com.webrtcmeetpro.resources.meeting_status_CANCELED
import com.webrtcmeetpro.resources.meeting_status_ENDED
import com.webrtcmeetpro.resources.meeting_status_LIVE
import com.webrtcmeetpro.resources.meeting_status_SCHEDULED
import com.webrtcmeetpro.resources.role_ADMIN
import com.webrtcmeetpro.resources.role_MEMBER
import com.webrtcmeetpro.resources.role_OWNER
import com.webrtcmeetpro.resources.teams_members
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** onClick 이 있을 때만 눌리게 */
fun Modifier.clickableIf(onClick: (() -> Unit)?): Modifier = if (onClick == null) this else clickable(onClick = onClick)

/** 서버가 "/uploads/..." 처럼 상대 경로로 주는 이미지를 절대 URL 로 */
@Composable
fun resolveImageUrl(path: String?): String? {
    val config = koinInject<ApiConfig>()
    return remember(path) { config.resolve(path) }
}

@Composable
fun MeetingCard(meeting: Meeting, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    LimeetCard(modifier.fillMaxWidth()) {
        Row(Modifier.clickableIf(onClick).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // 왼쪽 아이콘 칸
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        meeting.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    MeetingStatusBadge(meeting.status)
                }
                Text(
                    "${meeting.teamName} · ${formatDateTime(meeting.scheduledAt)} · " +
                        stringResource(Res.string.meeting_duration, meeting.durationMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val host = meeting.host
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (host != null) {
                        Avatar(host.nickname, resolveImageUrl(host.profileImageUrl), size = 20.dp)
                        Text(
                            stringResource(Res.string.meeting_host, host.nickname),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (meeting.status == MeetingStatus.LIVE && meeting.liveParticipants > 0) {
                        Text(
                            "· " + stringResource(Res.string.meeting_live_count, meeting.liveParticipants.toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = Live,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MeetingStatusBadge(status: MeetingStatus) {
    val label = stringResource(
        when (status) {
            MeetingStatus.SCHEDULED -> Res.string.meeting_status_SCHEDULED
            MeetingStatus.LIVE -> Res.string.meeting_status_LIVE
            MeetingStatus.ENDED -> Res.string.meeting_status_ENDED
            MeetingStatus.CANCELED -> Res.string.meeting_status_CANCELED
        },
    )
    when (status) {
        MeetingStatus.LIVE -> Badge(label, container = Live.copy(alpha = 0.15f), content = Live)
        MeetingStatus.SCHEDULED -> Badge(label)
        else -> Badge(label, container = MaterialTheme.colorScheme.surfaceVariant, content = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TeamCard(team: TeamSummary, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    LimeetCard(modifier.fillMaxWidth()) {
        Row(Modifier.clickableIf(onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // 팀 첫 글자 (보라 사각형)
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(team.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(team.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    team.description?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.teams_members, team.memberCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Badge(
                    stringResource(
                        when (team.myRole) {
                            TeamRole.OWNER -> Res.string.role_OWNER
                            TeamRole.ADMIN -> Res.string.role_ADMIN
                            TeamRole.MEMBER -> Res.string.role_MEMBER
                        },
                    ),
                )
                Text(
                    stringResource(Res.string.teams_members, team.memberCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
