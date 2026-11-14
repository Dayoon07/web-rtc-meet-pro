package com.webrtcmeetpro.domain.chat.dto.res

/** 이모지 하나에 단 사람들 */
data class ReactionResDto(val emoji: String, val count: Int, val userIds: List<Long>)
