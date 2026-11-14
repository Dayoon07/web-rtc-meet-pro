package com.webrtcmeetpro.domain.chat.dto.req

import com.webrtcmeetpro.common.web.userId
import jakarta.validation.constraints.NotNull

data class OpenDirectReqDto(@field:NotNull val userId: Long?)
