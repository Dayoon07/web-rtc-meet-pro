package com.webrtcmeetpro.common.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
) {
    init {
        require(secret.toByteArray().size >= 32) { "app.jwt.secret 은 32바이트 이상이어야 합니다." }
    }
}

@ConfigurationProperties("app.cors")
data class CorsProperties(
    val allowedOrigins: List<String>,
)
