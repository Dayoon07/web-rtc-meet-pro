package com.webrtcmeetpro.domain.push.dto.req

import com.webrtcmeetpro.domain.push.DevicePlatform
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 앱이 받은 FCM 등록 토큰 + 알림 문장 언어 */
data class PushDeviceReqDto(
    @field:NotBlank @field:Size(max = 500) val token: String,
    val platform: DevicePlatform = DevicePlatform.ANDROID,
    @field:Size(max = 10) val lang: String? = null,
)
