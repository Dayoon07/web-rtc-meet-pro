package com.webrtcmeetpro.domain.push.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 브라우저 PushSubscription.toJSON() 의 endpoint · keys.p256dh · keys.auth + 알림 문장 언어 */
data class PushSubscribeReqDto(
    @field:NotBlank @field:Size(max = 1000) val endpoint: String,
    @field:NotBlank @field:Size(max = 200) val p256dh: String,
    @field:NotBlank @field:Size(max = 100) val auth: String,
    @field:Size(max = 10) val lang: String? = null,
)
