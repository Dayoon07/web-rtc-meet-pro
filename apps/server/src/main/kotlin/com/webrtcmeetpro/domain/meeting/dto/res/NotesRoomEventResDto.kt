package com.webrtcmeetpro.domain.meeting.dto.res

/** /user/queue/meeting: AI 회의록이 켜지고 꺼짐 (회의실에 "AI 회의록 작성 중"). reason: QUOTA = 이번 달 시간을 다 씀 */
data class NotesRoomEventResDto(val meetingId: Long, val on: Boolean, val by: String? = null, val reason: String? = null) {
    val type: String = "AI_NOTES"
}
