package com.webrtcmeetpro.domain.auth.dto.res

data class AuthTokensResDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)
