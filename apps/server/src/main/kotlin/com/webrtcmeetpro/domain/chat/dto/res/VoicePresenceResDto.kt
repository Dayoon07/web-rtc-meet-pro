package com.webrtcmeetpro.domain.chat.dto.res

data class VoicePresenceResDto(val meetingId: Long, val participants: List<ChatUserResDto>)
