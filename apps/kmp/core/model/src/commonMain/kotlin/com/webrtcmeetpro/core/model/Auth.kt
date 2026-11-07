package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
enum class Gender { MALE, FEMALE, OTHER }

/** POST /api/auth/signup. phoneNumber 는 E.164 (+821012345678) */
@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val nickname: String,
    val realName: String,
    val phoneNumber: String,
    val countryCode: String,
    val birthYear: Int,
    val gender: Gender? = null,
    val termsAgreed: Boolean,
    val privacyAgreed: Boolean,
    val marketingAgreed: Boolean = false,
)

@Serializable
data class EmailAvailability(val available: Boolean)

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
    /** 예상하지 못한 에러(500)일 때만: 이 에러 1건의 번호 (ERR-YYYYMMDD-...). 사용자가 문의할 때 알려 준다 */
    val errorCode: String? = null,
    /** 예상하지 못한 에러일 때만: 대략적인 원인 (DB_CONNECTION 등) */
    val reason: String? = null,
)
