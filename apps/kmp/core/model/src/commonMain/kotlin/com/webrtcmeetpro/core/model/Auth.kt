package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

/** 서버 ErrorResponse. code 로 번역 문구를 찾는다 (composeResources 의 error_<CODE>) */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val params: Map<String, JsonElement> = emptyMap(),
    val field: String? = null,
)
