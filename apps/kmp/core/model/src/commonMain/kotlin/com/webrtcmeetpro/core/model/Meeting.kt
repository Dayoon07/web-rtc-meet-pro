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
    val startedAt: String? = null,
    val endedAt: String? = null,
    val host: MeetingUser? = null,
    val invitees: List<MeetingUser> = emptyList(),
    val liveParticipants: Long = 0,
) {
    val isOpen get() = status == MeetingStatus.SCHEDULED || status == MeetingStatus.LIVE
}

/** POST /api/teams/{id}/meetings. scheduledAt 을 비우면 지금 바로 시작. inviteeIds 를 비우면 팀원 전체 */
@Serializable
data class ScheduleMeetingRequest(
    val title: String,
    val description: String? = null,
    val scheduledAt: String? = null,
    val durationMinutes: Int = 60,
    val inviteeIds: List<Long> = emptyList(),
)

/** POST /api/sessions/web-handoff → 브라우저로 로그인 상태를 넘기는 60초짜리 1회용 코드 */
@Serializable
data class WebHandoff(val code: String)
