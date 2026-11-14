package com.webrtcmeetpro.domain.meeting.dto.res

data class JoinMeetingResDto(val meeting: MeetingResDto, val mediaUrl: String, val token: String)
