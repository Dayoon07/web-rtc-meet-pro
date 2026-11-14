package com.webrtcmeetpro.domain.presence.dto.req

import jakarta.validation.constraints.NotNull

/** away = true 면 자리 비움, false 면 돌아옴 */
data class PresenceReqDto(@field:NotNull val away: Boolean?)
