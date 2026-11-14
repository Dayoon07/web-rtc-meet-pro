package com.webrtcmeetpro.domain.meeting.dto.res

/** reason: QUOTA = 이번 달 시간을 다 씀 */
data class NotesRoomEventResDto(val meetingId: Long, val on: Boolean, val by: String? = null, val reason: String? = null) {
    val type: String = "AI_NOTES"
}
