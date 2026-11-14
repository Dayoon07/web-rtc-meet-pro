package com.webrtcmeetpro.domain.presence.dto.req

import jakarta.validation.constraints.NotNull

data class PresenceReqDto(@field:NotNull val away: Boolean?)
