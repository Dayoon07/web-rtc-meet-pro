package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * GET /api/notifications, STOMP /user/queue/notifications.
 * 서버는 type + params 만 저장하고, 문장은 앱이 현재 언어로 만든다.
 * type: TEAM_INVITATION, TEAM_INVITATION_ACCEPTED, CHAT_ROOM_INVITED,
 *       MEETING_SCHEDULED, MEETING_REMINDER, MEETING_STARTED, MEETING_CANCELED
 */
@Serializable
data class AppNotification(
    val id: Long,
    val type: String,
    val params: Map<String, JsonElement> = emptyMap(),
    /** 웹 경로 (예: /app/meetings/12, /app/invitations) */
    val link: String? = null,
    val read: Boolean = false,
    val createdAt: String,
) {
    fun param(key: String): String = (params[key] as? JsonPrimitive)?.content.orEmpty()
}
