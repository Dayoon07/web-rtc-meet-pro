package com.webrtcmeetpro.domain.meeting.dto.res

import com.webrtcmeetpro.domain.meeting.RecordingStatus
import java.time.LocalDateTime

data class RecordingResDto(
    val id: Long,
    val meetingId: Long,
    val startedBy: MeetingUserResDto?,
    val status: RecordingStatus,
    val sizeBytes: Long,
    val durationSeconds: Int?,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    /** 서명된 재생·내려받기 주소 (READY 일 때만) */
    val url: String?,
    /** 이 사람이 지울 수 있는지 (녹화한 사람, 주최자, 팀 관리자) */
    val canDelete: Boolean,
)
