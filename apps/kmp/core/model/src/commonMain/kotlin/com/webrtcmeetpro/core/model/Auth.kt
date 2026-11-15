package com.webrtcmeetpro.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

/** GET /api/auth/providers. 서버에 구글 키가 있어야 구글 로그인 버튼을 보여 준다 */
@Serializable
data class AuthProviders(val google: Boolean = false)

/** POST /api/auth/oauth/exchange. 구글 로그인이 끝나고 받은 1회용 코드를 토큰으로 바꾼다 */
@Serializable
data class OAuthExchangeRequest(val code: String)

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
data class EmailAvailability(val available: Boolean, val signInMethod: String? = null)

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
