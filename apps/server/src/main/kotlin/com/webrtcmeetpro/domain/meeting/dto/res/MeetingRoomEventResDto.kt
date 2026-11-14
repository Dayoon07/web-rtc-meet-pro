package com.webrtcmeetpro.domain.meeting.dto.res

/** /user/queue/meeting 으로 회의실 사람들에게: 녹화 시작·끝 (회의실에 "녹화 중" 표시) */
data class MeetingRoomEventResDto(val type: String, val meetingId: Long, val recording: Boolean, val by: String? = null)
