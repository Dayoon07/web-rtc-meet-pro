package com.webrtcmeetpro.domain.meeting.dto.res

data class MeetingRoomEventResDto(val type: String, val meetingId: Long, val recording: Boolean, val by: String? = null)
