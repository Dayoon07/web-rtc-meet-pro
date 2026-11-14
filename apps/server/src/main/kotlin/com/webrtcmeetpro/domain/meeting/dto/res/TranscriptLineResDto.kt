package com.webrtcmeetpro.domain.meeting.dto.res

import java.time.LocalDateTime

data class TranscriptLineResDto(val id: Long, val userId: Long, val speaker: String, val text: String, val at: LocalDateTime)
