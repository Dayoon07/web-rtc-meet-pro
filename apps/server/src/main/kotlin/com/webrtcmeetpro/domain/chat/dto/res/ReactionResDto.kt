package com.webrtcmeetpro.domain.chat.dto.res

data class ReactionResDto(val emoji: String, val count: Int, val userIds: List<Long>)
