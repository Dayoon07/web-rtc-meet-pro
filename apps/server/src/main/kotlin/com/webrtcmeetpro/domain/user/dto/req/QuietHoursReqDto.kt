package com.webrtcmeetpro.domain.user.dto.req

import jakarta.validation.constraints.Size

/** "HH:mm". 둘 다 비우면 끄기 */
data class QuietHoursReqDto(
    @field:Size(max = 5) val start: String? = null,
    @field:Size(max = 5) val end: String? = null,
    @field:Size(max = 50) val timeZone: String? = null,
)
