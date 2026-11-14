package com.webrtcmeetpro.domain.push.dto.res

/** enabled=false 면 서버에 VAPID 키가 없다 */
data class PushConfigResDto(val enabled: Boolean, val publicKey: String?)
