package com.webrtcmeetpro.infrastructure.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.webrtcmeetpro.config.properties.JwtProperties
import java.time.Instant
import javax.crypto.spec.SecretKeySpec
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component

@Component
class JwtTokenProvider(private val props: JwtProperties) {

    private val key = SecretKeySpec(props.secret.toByteArray(), "HmacSHA256")
    private val encoder = NimbusJwtEncoder(ImmutableSecret(key))

    val accessTokenTtlSeconds: Long get() = props.accessTokenTtl.seconds

    fun issueAccessToken(userId: Long, email: String, nickname: String): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(props.issuer)
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(props.accessTokenTtl))
            .claim("email", email)
            .claim("nickname", nickname)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    fun decoder(): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
}
