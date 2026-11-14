package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.NotNull

data class MuteReqDto(@field:NotNull val muted: Boolean?)
