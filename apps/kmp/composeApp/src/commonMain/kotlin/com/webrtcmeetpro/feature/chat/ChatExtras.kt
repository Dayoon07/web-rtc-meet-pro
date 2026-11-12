@file:OptIn(ExperimentalLayoutApi::class)

package com.webrtcmeetpro.feature.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webrtcmeetpro.core.designsystem.component.Avatar
import com.webrtcmeetpro.core.model.ChatMessage
import com.webrtcmeetpro.core.model.ChatNotice
import com.webrtcmeetpro.core.model.ChatUser
import com.webrtcmeetpro.core.model.PresenceStatus
import com.webrtcmeetpro.core.model.Reaction
import com.webrtcmeetpro.core.model.ReplyPreview
import com.webrtcmeetpro.platform.formatChatTime
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.chat_cancel_reply
import com.webrtcmeetpro.resources.chat_delete
import com.webrtcmeetpro.resources.chat_deleted_message
import com.webrtcmeetpro.resources.chat_edit
import com.webrtcmeetpro.resources.chat_emoji
import com.webrtcmeetpro.resources.chat_file_many
import com.webrtcmeetpro.resources.chat_notice_edit
import com.webrtcmeetpro.resources.chat_notice_from_message
import com.webrtcmeetpro.resources.chat_notice_hint
import com.webrtcmeetpro.resources.chat_notice_label
import com.webrtcmeetpro.resources.chat_notice_remove
import com.webrtcmeetpro.resources.chat_notice_save
import com.webrtcmeetpro.resources.chat_notice_title
import com.webrtcmeetpro.resources.chat_reply
import com.webrtcmeetpro.resources.chat_reply_to
import com.webrtcmeetpro.resources.chat_typing_many
import com.webrtcmeetpro.resources.chat_typing_one
import com.webrtcmeetpro.resources.common_cancel
import com.webrtcmeetpro.resources.presence_away
import com.webrtcmeetpro.resources.presence_offline
import com.webrtcmeetpro.resources.presence_online
import com.webrtcmeetpro.ui.resolveImageUrl
import org.jetbrains.compose.resources.stringResource

/** 이모지 고르기 (운영체제 이모지 글꼴로 그린다). 탭 아이콘 → 목록 */
object ChatEmoji {
    val QUICK = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
    val CATEGORIES: List<Pair<String, List<String>>> = listOf(
        "😀" to "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 😜 🤪 🤗 🤭 🤔 🤐 😐 😏 🙄 😬 😌 😔 😴 😷 🤒 🥵 🥶 🤯 🥳 😎 🤓 😕 😟 😮 😲 😳 🥺 😢 😭 😱 😩 😤 😡 🤬 💀 🤖".split(" "),
        "👋" to "👍 👎 👌 ✌️ 🤞 🤟 🤙 👈 👉 👆 👇 ✋ 👋 👏 🙌 🙏 🤝 💪 ✍️ 👀 🙇 🙋 🙆 🙅 🤷 🤦 🧑‍💻 💃 🕺".split(" "),
        "🐶" to "🐶 🐱 🐰 🦊 🐻 🐼 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐤 🦄 🐝 🦋 🐢 🐙 🐬 🐳 🌸 🌻 🌱 🍀 🌈 ☀️ ⭐ 🌙 ⚡ ❄️ 🔥 💧".split(" "),
        "🍔" to "🍎 🍊 🍋 🍌 🍉 🍇 🍓 🍑 🍍 🥑 🥕 🌽 🍞 🧀 🍳 🍗 🍔 🍟 🍕 🌮 🥗 🍝 🍜 🍣 🍱 🍙 🍰 🎂 🍫 🍩 🍪 ☕ 🍵 🧋 🍺 🍷".split(" "),
        "⚽" to "⚽ 🏀 ⚾ 🎾 🏐 🏓 🏸 🥊 🎯 ⛳ 🏆 🥇 🎨 🎬 🎤 🎧 🎹 🎸 🎲 🎮 🧩 🎉 🎊 🎈 🎁".split(" "),
        "💡" to "⌚ 📱 💻 ⌨️ 🖥️ 📷 📞 ⏰ 💡 💰 💳 🔧 🔨 ⚙️ 🔑 🔒 📦 📝 📄 📊 📈 📅 📋 📁 📌 📎 ✂️ 🔍 📚 📢 🔔".split(" "),
        "❤️" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 💕 💯 ✨ 💥 💬 ✅ ❌ ➕ ❓ ❗ ⭕ 🚫 🔴 🟢 🔵 ⚠️ ♻️ 💤".split(" "),
    )
}

/** 프로필 사진 오른쪽 아래 점: 초록 접속 중 · 노랑 자리 비움 · 회색 접속 안 함 */
@Composable
fun PresenceDot(status: PresenceStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        PresenceStatus.ONLINE -> Color(0xFF22C55E)
        PresenceStatus.AWAY -> Color(0xFFFBBF24)
        PresenceStatus.OFFLINE -> Color(0xFFD1D5DB)
    }
    Box(
        modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun presenceLabel(status: PresenceStatus): String = stringResource(
    when (status) {
        PresenceStatus.ONLINE -> Res.string.presence_online
        PresenceStatus.AWAY -> Res.string.presence_away
        PresenceStatus.OFFLINE -> Res.string.presence_offline
    },
)

/** http(s)·www·흔한 도메인 주소 (웹과 같은 규칙) */
private val URL_RE = Regex(
    """\b((?:https?://|www\.)[^\s<>"']+|(?:[a-z0-9-]+\.)+(?:com|net|org|kr|io|dev|app|me|ai|jp|cn|co|so|xyz|site|shop|link|info|biz|gg|tv|ly|to)(?:/[^\s<>"']*)?)""",
    RegexOption.IGNORE_CASE,
)
private val TRAILING = Regex("""[.,!?;:)\]}'"…。、]+$""")

/**
 * 메시지 글: 주소는 누르면 열리는 파란 링크, "@닉네임"·"@모두" 는 강조 (나를 부르면 노랗게).
 * onDark = 내 말풍선(보라 배경) 위라 밝은 색을 쓴다
 */
@Composable
fun richMessageText(text: String, mentionNames: List<String>, myName: String?, onDark: Boolean): AnnotatedString {
    val link = if (onDark) Color(0xFFBAE6FD) else Color(0xFF2563EB)
    val mentionBg = if (onDark) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primaryContainer
    val meBg = Color(0xFFFDE68A)
    val names = mentionNames.distinct().sortedByDescending { it.length }
    val mentionRe = if (names.isEmpty()) null else Regex("@(" + names.joinToString("|") { Regex.escape(it) } + ")")
    // (시작, 끝, 종류, 값): 링크와 멘션 위치를 모아 앞에서부터 이어 붙인다
    val spans = mutableListOf<Triple<IntRange, String, String>>()
    mentionRe?.findAll(text)?.forEach { spans += Triple(it.range, "mention", it.groupValues[1]) }
    URL_RE.findAll(text).forEach { m ->
        if (m.range.first > 0 && text[m.range.first - 1] == '@') return@forEach
        val label = m.value.replace(TRAILING, "")
        if (!label.contains('.')) return@forEach
        val range = m.range.first until m.range.first + label.length
        if (spans.none { it.first.first <= range.last && range.first <= it.first.last }) spans += Triple(range, "link", label)
    }
    spans.sortBy { it.first.first }
    return buildAnnotatedString {
        var at = 0
        spans.forEach { (range, kind, value) ->
            if (range.first > at) append(text.substring(at, range.first))
            val piece = text.substring(range.first, range.last + 1)
            if (kind == "link") {
                val url = if (value.startsWith("http", ignoreCase = true)) value else "https://$value"
                withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)))) { append(piece) }
            } else {
                // 나를 부르거나 @모두 면 노랗게
                val forMe = value == myName || value in ALL_LABELS
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        background = if (forMe) meBg else mentionBg,
                        color = if (forMe) Color(0xFF78350F) else Color.Unspecified,
                    ),
                ) { append(piece) }
            }
            at = range.last + 1
        }
        if (at < text.length) append(text.substring(at))
    }
}

/** 언어마다 "@모두" 이름 (어느 언어로 보냈든 강조) */
val ALL_LABELS = setOf("모두", "all", "全員", "所有人")

/** 방 위쪽 공지: 한 줄 → 누르면 펼침. 고칠 수 있으면 연필 */
@Composable
fun NoticeBar(notice: ChatNotice, onEdit: (() -> Unit)?) {
    var open by remember(notice.at) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            .clickable { open = !open }.padding(horizontal = 14.dp, vertical = 8.dp).animateContentSize(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.chat_notice_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(
                if (open) richMessageText(notice.content, emptyList(), null, false) else AnnotatedString(notice.content.replace(Regex("\\s+"), " ")),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (open) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (open) {
                Text(
                    listOfNotNull(notice.by?.nickname, notice.at?.let(::formatChatTime)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.chat_notice_edit), modifier = Modifier.size(16.dp))
            }
        }
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/** 공지 쓰기·고치기·내리기 창 */
@Composable
fun NoticeDialog(initial: String, canRemove: Boolean, onSave: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.chat_notice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(1000) }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
                Text(stringResource(Res.string.chat_notice_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank() && text.trim() != initial) { Text(stringResource(Res.string.chat_notice_save)) }
        },
        dismissButton = {
            Row {
                if (canRemove) TextButton(onClick = onRemove) { Text(stringResource(Res.string.chat_notice_remove), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
            }
        },
    )
}

/** 답장한 원래 메시지 (말풍선 위) */
@Composable
fun ReplyQuote(reply: ReplyPreview, onDark: Boolean) {
    val preview = when {
        reply.deleted -> stringResource(Res.string.chat_deleted_message)
        !reply.content.isNullOrBlank() -> reply.content!!
        reply.fileCount > 0 -> stringResource(Res.string.chat_file_many, reply.fileCount)
        else -> ""
    }
    Row(
        Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(if (onDark) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (onDark) Color.White else MaterialTheme.colorScheme.primary)
        Column {
            Text(reply.sender?.nickname.orEmpty(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = if (onDark) Color.White else Color.Unspecified)
            Text(
                preview,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontStyle = if (reply.deleted) FontStyle.Italic else FontStyle.Normal,
                color = if (onDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 메시지 아래 반응: 👍 3. 내가 단 것은 진하게, 누르면 달기·떼기 */
@Composable
fun ReactionChips(reactions: List<Reaction>, myId: Long, onToggle: (String) -> Unit) {
    if (reactions.isEmpty()) return
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        reactions.forEach { r ->
            val mine = myId in r.userIds
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .border(1.dp, if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                    .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { onToggle(r.emoji) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.emoji, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                Text("${r.count}", style = MaterialTheme.typography.labelSmall, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** 메시지를 길게 눌렀을 때: 빠른 반응 · 답장 · 공지로 · 고치기 · 지우기 */
@Composable
fun MessageActionsDialog(
    message: ChatMessage,
    actions: MessageActions,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                message.content?.let { Text(it.take(200), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChatEmoji.QUICK.forEach { e ->
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { actions.react(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 22.sp) }
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = actions.moreEmoji), contentAlignment = Alignment.Center) { Text("➕", fontSize = 18.sp) }
                }
                Column {
                    TextButton(onClick = actions.reply) { Text(stringResource(Res.string.chat_reply)) }
                    actions.makeNotice?.let { TextButton(onClick = it) { Text(stringResource(Res.string.chat_notice_from_message)) } }
                    actions.edit?.let { TextButton(onClick = it) { Text(stringResource(Res.string.chat_edit)) } }
                    actions.delete?.let { TextButton(onClick = it) { Text(stringResource(Res.string.chat_delete), color = MaterialTheme.colorScheme.error) } }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** 메시지 메뉴에서 할 수 있는 일 (없으면 null = 버튼을 안 보인다) */
data class MessageActions(
    val react: (String) -> Unit,
    val moreEmoji: () -> Unit,
    val reply: () -> Unit,
    val makeNotice: (() -> Unit)?,
    val edit: (() -> Unit)?,
    val delete: (() -> Unit)?,
)

/** 이모지 고르기 창 (분류 탭) */
@Composable
fun EmojiPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.chat_emoji)) },
        text = {
            Column {
                SecondaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    ChatEmoji.CATEGORIES.forEachIndexed { i, (icon, _) ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(icon, fontSize = 18.sp) })
                    }
                }
                LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.heightIn(max = 300.dp).padding(top = 8.dp)) {
                    items(ChatEmoji.CATEGORIES[tab].second) { e ->
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).clickable { onPick(e) }, contentAlignment = Alignment.Center) {
                            Text(e, fontSize = 24.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** "@" 를 치면 입력창 위에 뜨는 방 사람들 (누르면 "@닉네임 ") */
@Composable
fun MentionSuggestions(options: List<ChatUser?>, allLabel: String, onPick: (ChatUser?) -> Unit) {
    if (options.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(options) { u ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { onPick(u) }
                    .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (u != null) Avatar(u.nickname, resolveImageUrl(u.profileImageUrl), size = 24.dp)
                else Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text("@", style = MaterialTheme.typography.labelSmall) }
                Text("@" + (u?.nickname ?: allLabel), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 입력창 위 답장 표시 */
@Composable
fun ReplyBar(target: ChatMessage, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            stringResource(Res.string.chat_reply_to, target.sender?.nickname.orEmpty()) + " · " + (target.content ?: ""),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.chat_cancel_reply), modifier = Modifier.size(16.dp))
        }
    }
}

/** "○○님이 입력 중..." */
@Composable
fun TypingLine(users: List<ChatUser>) {
    if (users.isEmpty()) return
    Text(
        if (users.size == 1) stringResource(Res.string.chat_typing_one, users[0].nickname)
        else stringResource(Res.string.chat_typing_many, users[0].nickname, users.size - 1),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
    )
}
