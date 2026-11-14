package com.webrtcmeetpro.domain.meeting.dto.res

/** 회의실 화면용: 켜져 있는지, 켤 수 있는지, 이번 달 남은 시간 */
data class NotesStateResDto(val on: Boolean, val available: Boolean, val remainingMinutes: Long, val limitMinutes: Long)
