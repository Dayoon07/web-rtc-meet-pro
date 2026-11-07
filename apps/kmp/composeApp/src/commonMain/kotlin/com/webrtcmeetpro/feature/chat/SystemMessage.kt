package com.webrtcmeetpro.feature.chat

import androidx.compose.runtime.Composable
import com.webrtcmeetpro.core.model.ChatFile
import com.webrtcmeetpro.core.model.ChatMessage
import com.webrtcmeetpro.core.model.MessageType
import com.webrtcmeetpro.core.network.ApiJson
import com.webrtcmeetpro.resources.Res
import com.webrtcmeetpro.resources.chat_file_many
import com.webrtcmeetpro.resources.chat_file_one
import com.webrtcmeetpro.resources.chat_file_photo
import com.webrtcmeetpro.resources.chat_file_photos
import com.webrtcmeetpro.resources.chat_sys_created
import com.webrtcmeetpro.resources.chat_sys_created_with
import com.webrtcmeetpro.resources.chat_sys_invited
import com.webrtcmeetpro.resources.chat_sys_left
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** 서버가 SYSTEM 메시지 본문에 넣는 JSON: {"key":"invited","actor":"서준","targets":["하늘"]} */
@Serializable
private data class SystemPayload(val key: String, val actor: String = "", val targets: List<String> = emptyList())

/** SYSTEM 메시지는 현재 언어 문장으로, 일반 메시지는 그대로 (웹 chat.system.* 과 같은 규칙) */
@Composable
fun messageText(message: ChatMessage): String {
    // 글 없이 파일만 보낸 FILE 메시지는 "사진 3장" 같은 요약
    if (message.type != MessageType.SYSTEM) return message.content ?: filesSummary(message.files)
    val raw = message.content.orEmpty()
    val payload = runCatching { ApiJson.decodeFromString<SystemPayload>(raw) }.getOrNull() ?: return raw
    val targets = payload.targets.joinToString(", ")
    return when (payload.key) {
        "created" -> if (payload.targets.isEmpty()) stringResource(Res.string.chat_sys_created, payload.actor)
        else stringResource(Res.string.chat_sys_created_with, payload.actor, targets)
        "invited" -> stringResource(Res.string.chat_sys_invited, payload.actor, targets)
        "left" -> stringResource(Res.string.chat_sys_left, payload.actor)
        else -> raw
    }
}

/** 글 없이 파일만 보낸 메시지의 미리보기: 사진 / 사진 3장 / 파일 / 파일 2개 (웹 filesSummary 와 같은 규칙) */
@Composable
fun filesSummary(files: List<ChatFile>): String {
    val n = files.size
    return if (files.all { it.image }) {
        if (n == 1) stringResource(Res.string.chat_file_photo) else stringResource(Res.string.chat_file_photos, n)
    } else {
        if (n == 1) stringResource(Res.string.chat_file_one) else stringResource(Res.string.chat_file_many, n)
    }
}

/** 알림처럼 화면 밖에서 쓰는 미리보기 문장 (일반·파일 메시지) */
suspend fun messagePreviewAsync(message: ChatMessage): String {
    message.content?.let { return it }
    val n = message.files.size
    return if (message.files.all { it.image }) {
        if (n == 1) getString(Res.string.chat_file_photo) else getString(Res.string.chat_file_photos, n)
    } else {
        if (n == 1) getString(Res.string.chat_file_one) else getString(Res.string.chat_file_many, n)
    }
}
