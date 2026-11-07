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
    /** 내가 취소할 수 있는지 (예약 상태 + 주최자이거나 팀 관리 권한). 서버가 계산 */
    val canCancel: Boolean = false,
    /** 내가 고칠 수 있는지 (취소와 같은 조건) */
    val canEdit: Boolean = false,
    /** 채널 상시 음성방이면 그 채널 */
    val chatRoomId: Long? = null,
    /** AI 회의록 상태 (RECORDING/PAUSED/PROCESSING/READY/FAILED/EMPTY). 켠 적이 없으면 null */
    val notesStatus: String? = null,
) {
    val isOpen get() = status == MeetingStatus.SCHEDULED || status == MeetingStatus.LIVE
}

/** GET /api/meetings/{id}/notes: AI 회의록 (요약·결정 사항·할 일) */
@Serializable
data class MeetingNotes(
    val meetingId: Long,
    val status: String,
    val on: Boolean = false,
    val title: String? = null,
    val summary: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val actionItems: List<NotesActionItem> = emptyList(),
    val generatedAt: String? = null,
    val minutes: Long = 0,
    val lineCount: Int = 0,
    val canRegenerate: Boolean = false,
)

@Serializable
data class NotesActionItem(val task: String, val owner: String? = null, val ownerUserId: Long? = null, val due: String? = null)

/** GET /api/meetings/{id}/recordings. url 은 서명된 재생 주소 (READY 일 때만, "/api/..." 상대 경로) */
@Serializable
data class Recording(
    val id: Long,
    val meetingId: Long,
    val startedBy: MeetingUser? = null,
    val status: String,
    val sizeBytes: Long = 0,
    val durationSeconds: Int? = null,
    val startedAt: String,
    val url: String? = null,
)

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
