package com.webrtcmeetpro.domain.presence.dto.res

import com.webrtcmeetpro.domain.presence.PresenceStatus

data class PresenceResDto(val userId: Long, val status: PresenceStatus)
