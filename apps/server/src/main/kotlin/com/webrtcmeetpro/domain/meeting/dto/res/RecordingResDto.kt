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
    /** READY 일 때만 */
    val url: String?,
    val canDelete: Boolean,
)
