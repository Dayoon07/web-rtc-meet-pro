package com.webrtcmeetpro.domain.chat.dto.res

/** 방 사람이 어디까지 읽었는지 (안 읽은 사람 수 계산) */
data class ReadStateResDto(val userId: Long, val lastReadMessageId: Long?)
