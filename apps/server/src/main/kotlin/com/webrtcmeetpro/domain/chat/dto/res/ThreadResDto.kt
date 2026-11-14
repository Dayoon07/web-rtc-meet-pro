package com.webrtcmeetpro.domain.chat.dto.res

data class ThreadResDto(val root: ChatMessageResDto, val replies: List<ChatMessageResDto>)
