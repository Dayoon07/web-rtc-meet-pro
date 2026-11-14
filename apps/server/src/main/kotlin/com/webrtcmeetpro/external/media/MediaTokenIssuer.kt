package com.webrtcmeetpro.external.media

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.webrtcmeetpro.config.properties.MediaProperties
import java.time.Duration
import java.time.Instant
import javax.crypto.spec.SecretKeySpec
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component

/** 사이드카 토큰이 새어도 API 를 쓸 수 없게 액세스 토큰과 키·audience 를 분리한다 */
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
