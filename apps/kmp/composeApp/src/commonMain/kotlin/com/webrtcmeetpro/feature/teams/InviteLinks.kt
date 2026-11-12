package com.webrtcmeetpro.feature.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webrtcmeetpro.core.designsystem.component.LimeetCard
import com.webrtcmeetpro.core.designsystem.component.PrimaryButton
import com.webrtcmeetpro.core.model.InviteLink
import com.webrtcmeetpro.core.model.InviteLinkPreview
import com.webrtcmeetpro.platform.formatDateTime
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.common_cancel
import com.webrtcmeetpro.resources.invite_link_already_member
import com.webrtcmeetpro.resources.invite_link_check
import com.webrtcmeetpro.resources.invite_link_copy
import com.webrtcmeetpro.resources.invite_link_create
import com.webrtcmeetpro.resources.invite_link_delete
import com.webrtcmeetpro.resources.invite_link_delete_confirm
import com.webrtcmeetpro.resources.invite_link_empty
import com.webrtcmeetpro.resources.invite_link_go_chat
import com.webrtcmeetpro.resources.invite_link_join
import com.webrtcmeetpro.resources.invite_link_join_title
import com.webrtcmeetpro.resources.invite_link_members
import com.webrtcmeetpro.resources.invite_link_meta
import com.webrtcmeetpro.resources.invite_link_paste
import com.webrtcmeetpro.resources.invite_link_password
import com.webrtcmeetpro.resources.invite_link_password_hint
import com.webrtcmeetpro.resources.invite_link_title
import com.webrtcmeetpro.resources.invite_link_use_password
import org.jetbrains.compose.resources.stringResource

/**
 * 팀 상세의 "초대 링크" (소유자·관리자): 링크 만들기(비밀번호 선택) · 복사 · 지우기.
 * 링크는 기간 제한이 없고, 지우면 그 링크로는 더 못 들어온다. onCopied: 복사했다고 알리기
 */
@Composable
fun InviteLinksSection(
    links: List<InviteLink>,
    linkUrl: (InviteLink) -> String,
    creating: Boolean,
    onCreate: (password: String?) -> Unit,
    onDelete: (InviteLink) -> Unit,
    onCopied: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<InviteLink?>(null) }

    Text(stringResource(Res.string.invite_link_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    LimeetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = usePassword, onCheckedChange = { usePassword = it; if (!it) password = "" })
                Text(stringResource(Res.string.invite_link_use_password), style = MaterialTheme.typography.bodyMedium)
            }
            if (usePassword) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = { Text(stringResource(Res.string.invite_link_password)) },
                    supportingText = { Text(stringResource(Res.string.invite_link_password_hint)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PrimaryButton(
                stringResource(Res.string.invite_link_create),
                onClick = {
                    onCreate(password.takeIf { usePassword })
                    password = ""
                    usePassword = false
                },
                enabled = !usePassword || password.length >= 4,
                loading = creating,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    LimeetCard(Modifier.fillMaxWidth()) {
        if (links.isEmpty()) {
            Text(
                stringResource(Res.string.invite_link_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Column {
                links.forEachIndexed { i, link ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (link.hasPassword) Icons.Outlined.Lock else Icons.Outlined.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(linkUrl(link), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(Res.string.invite_link_meta, link.createdByName.orEmpty(), formatDateTime(link.createdAt), link.joinCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(linkUrl(link))); onCopied() }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(Res.string.invite_link_copy))
                        }
                        IconButton(onClick = { confirmDelete = link }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.invite_link_delete))
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { link ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(Res.string.invite_link_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; onDelete(link) }) {
                    Text(stringResource(Res.string.invite_link_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}

/** 초대 링크로 들어가기: 링크(또는 링크 끝 코드)를 붙여 넣고 → 팀 확인 → (비밀번호) → 들어가기 */
@Composable
fun JoinByLinkCard(
    preview: InviteLinkPreview?,
    busy: Boolean,
    onCheck: (link: String) -> Unit,
    onJoin: (password: String?) -> Unit,
) {
    var link by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    LimeetCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.invite_link_join_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it.take(500) },
                    label = { Text(stringResource(Res.string.invite_link_paste)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { onCheck(link) }, enabled = link.isNotBlank() && !busy) { Text(stringResource(Res.string.invite_link_check)) }
            }
            preview?.let { p ->
                Text(p.teamName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                p.teamDescription?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(Res.string.invite_link_members, p.memberCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.alreadyMember) {
                    Text(stringResource(Res.string.invite_link_already_member), style = MaterialTheme.typography.bodySmall)
                    PrimaryButton(stringResource(Res.string.invite_link_go_chat), onClick = { onJoin(null) }, loading = busy, modifier = Modifier.fillMaxWidth())
                } else {
                    if (p.hasPassword) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it.take(64) },
                            label = { Text(stringResource(Res.string.invite_link_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    PrimaryButton(
                        stringResource(Res.string.invite_link_join),
                        onClick = { onJoin(password.takeIf { p.hasPassword }) },
                        enabled = !p.hasPassword || password.isNotEmpty(),
                        loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 붙여 넣은 링크(https://…/join/코드)나 코드에서 코드만 */
fun inviteTokenOf(input: String): String =
    input.trim().substringAfterLast("/join/").substringBefore('?').substringBefore('#').trim('/').trim()
