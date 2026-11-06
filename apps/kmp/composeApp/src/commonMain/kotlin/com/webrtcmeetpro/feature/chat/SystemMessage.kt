package com.webrtcmeetpro.feature.chat

import androidx.compose.runtime.Composable
import com.webrtcmeetpro.core.model.ChatMessage
import com.webrtcmeetpro.core.model.MessageType
import com.webrtcmeetpro.core.network.ApiJson
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.chat_sys_created
import com.webrtcmeetpro.resources.chat_sys_created_with
import com.webrtcmeetpro.resources.chat_sys_invited
import com.webrtcmeetpro.resources.chat_sys_left
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

/** 서버가 SYSTEM 메시지 본문에 넣는 JSON: {"key":"invited","actor":"서준","targets":["하늘"]} */
@Serializable
private data class SystemPayload(val key: String, val actor: String = "", val targets: List<String> = emptyList())

/** SYSTEM 메시지는 현재 언어 문장으로, 일반 메시지는 그대로 (웹 chat.system.* 과 같은 규칙) */
@Composable
fun messageText(message: ChatMessage): String {
    if (message.type != MessageType.SYSTEM) return message.content
    val payload = runCatching { ApiJson.decodeFromString<SystemPayload>(message.content) }.getOrNull() ?: return message.content
    val targets = payload.targets.joinToString(", ")
    return when (payload.key) {
        "created" -> if (payload.targets.isEmpty()) stringResource(Res.string.chat_sys_created, payload.actor)
        else stringResource(Res.string.chat_sys_created_with, payload.actor, targets)
        "invited" -> stringResource(Res.string.chat_sys_invited, payload.actor, targets)
        "left" -> stringResource(Res.string.chat_sys_left, payload.actor)
        else -> message.content
    }
}
