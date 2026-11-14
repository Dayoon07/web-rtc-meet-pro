package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * jwtSecret 은 사이드카의 MEDIA_JWT_SECRET, internalSecret 은 INTERNAL_SECRET 과 같아야 한다.
 * publicUrl 을 비우면 같은 출처(/media/socket.io)로 붙는다.
 */
@ConfigurationProperties("app.media")
data class MediaProperties(
    val jwtSecret: String,
    val internalSecret: String,
    val publicUrl: String = "",
) {
    init {
        require(jwtSecret.toByteArray().size >= 32) { "app.media.jwt-secret 은 32바이트 이상이어야 합니다." }
    }
}
