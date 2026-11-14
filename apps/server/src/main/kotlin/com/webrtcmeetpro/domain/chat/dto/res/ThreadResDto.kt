package com.webrtcmeetpro.domain.chat.dto.res

/** 스레드 창: 시작 글 + 답글(오래된 순) */
data class ThreadResDto(val root: ChatMessageResDto, val replies: List<ChatMessageResDto>)
