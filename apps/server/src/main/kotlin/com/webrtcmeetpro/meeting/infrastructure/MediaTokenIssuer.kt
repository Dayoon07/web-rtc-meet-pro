package com.webrtcmeetpro.meeting.infrastructure

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

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

/** 사이드카에 접속할 때만 쓰는 짧은 토큰. API 액세스 토큰과 키·audience 를 분리한다 */
@Component
class MediaTokenIssuer(props: MediaProperties) {

    private val encoder = NimbusJwtEncoder(ImmutableSecret(SecretKeySpec(props.jwtSecret.toByteArray(), "HmacSHA256")))

    fun issue(userId: Long, meetingId: Long, roomCode: String, displayName: String, avatarUrl: String?, isHost: Boolean): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("web-rtc-meet-pro")
            .audience(listOf(AUDIENCE))
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(TTL))
            .claim("mid", meetingId)
            .claim("room", roomCode)
            .claim("name", displayName)
            .claim("host", isHost)
            .apply { if (avatarUrl != null) claim("avatar", avatarUrl) }
            .build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    companion object {
        const val AUDIENCE = "wrmp-media"
        val TTL: Duration = Duration.ofMinutes(2)
    }
}
