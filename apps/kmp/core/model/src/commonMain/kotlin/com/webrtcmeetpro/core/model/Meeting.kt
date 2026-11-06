package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MeetingStatus { SCHEDULED, LIVE, ENDED, CANCELED }

@Serializable
data class MeetingUser(val id: Long, val nickname: String, val profileImageUrl: String? = null)

/** GET /api/meetings?scope=upcoming|past. 시간은 오프셋 포함 ISO-8601 (예: 2026-11-06T10:00:00+09:00) */
@Serializable
data class Meeting(
    val id: Long,
    val teamId: Long,
    val teamName: String,
    val title: String,
    val description: String? = null,
    val status: MeetingStatus,
    val scheduledAt: String,
    val durationMinutes: Int,
    val host: MeetingUser? = null,
    val invitees: List<MeetingUser> = emptyList(),
    val liveParticipants: Long = 0,
)
