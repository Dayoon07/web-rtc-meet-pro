package com.webrtcmeetpro.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * mediasoup 사이드카 연동 설정.
 * - jwtSecret: 회의 입장 토큰 서명 키 (사이드카의 MEDIA_JWT_SECRET 과 같아야 함)
 * - internalSecret: 사이드카 → 서버 이벤트 콜백 인증 헤더 (사이드카의 INTERNAL_SECRET 과 같아야 함)
 * - publicUrl: 브라우저가 붙을 사이드카 주소. 비우면 같은 출처(/media/socket.io, dev 는 Vite 프록시)
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
