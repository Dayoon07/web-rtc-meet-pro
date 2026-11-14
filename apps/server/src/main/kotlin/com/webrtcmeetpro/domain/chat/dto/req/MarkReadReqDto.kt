package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.NotNull

data class MarkReadReqDto(@field:NotNull val messageId: Long?)
