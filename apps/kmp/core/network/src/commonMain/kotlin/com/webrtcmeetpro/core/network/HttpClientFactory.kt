package com.webrtcmeetpro.core.network

import com.webrtcmeetpro.core.model.AuthTokens
import com.webrtcmeetpro.core.model.ErrorResponse
import com.webrtcmeetpro.core.model.RefreshRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * 서버 주소. Android 에뮬레이터는 10.0.2.2, 데스크톱은 localhost (플랫폼 DI 모듈에서 정함).
 * webUrl 은 웹 클라이언트 주소 — 화상회의처럼 아직 브라우저에서만 되는 화면을 열 때 쓴다.
 */
/**
 * @param language 지금 앱 언어 (ko/en/ja/zh). 서버가 보내는 메일(팀 초대 등)을 같은 언어로 쓰게 Accept-Language 로 보낸다
 */
data class ApiConfig(
    val baseUrl: String,
    val webUrl: String,
    val language: () -> String = { "ko" },
    /** 서버 기록(로그인 기록·에러 기록)에 남는 앱 이름. 예: Limeet-App (Android 14; Pixel 7) */
    val userAgent: String = "Limeet-App",
) {
    /** 프로필 이미지처럼 "/uploads/..." 로 오는 경로를 절대 URL 로 */
    fun resolve(pathOrUrl: String?): String? = when {
        pathOrUrl.isNullOrBlank() -> null
        pathOrUrl.startsWith("/") -> baseUrl.trimEnd('/') + pathOrUrl
        else -> pathOrUrl
    }
}

/** core:data 의 SessionStore 가 구현. network 는 저장 방식을 모른다 */
interface TokenStorage {
    suspend fun load(): AuthTokens?
    suspend fun save(tokens: AuthTokens)
    suspend fun clear()
}

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * - Authorization: Bearer 자동 첨부, 401 이면 /api/auth/refresh 로 토큰 회전 후 재시도
 * - 갱신도 실패하면 저장된 토큰을 지운다 → 앱이 로그인 화면으로 돌아간다
 * - 2xx 가 아니면 서버 ErrorResponse 를 ApiException 으로 바꿔 던진다
 */
fun createHttpClient(config: ApiConfig, tokens: TokenStorage): HttpClient = HttpClient {
    expectSuccess = true

    defaultRequest {
        url(config.baseUrl.trimEnd('/') + "/")
        contentType(ContentType.Application.Json)
        // defaultRequest 는 요청마다 실행된다 → 설정에서 언어를 바꾸면 다음 요청부터 바로 반영
        header(HttpHeaders.AcceptLanguage, config.language())
        header(HttpHeaders.UserAgent, config.userAgent)
    }
    install(ContentNegotiation) { json(ApiJson) }
    install(WebSockets)
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
    }
    install(Auth) {
        bearer {
            loadTokens { tokens.load()?.let { BearerTokens(it.accessToken, it.refreshToken) } }
            refreshTokens {
                val refresh = oldTokens?.refreshToken ?: return@refreshTokens null
                try {
                    val renewed = client.post("api/auth/refresh") {
                        markAsRefreshTokenRequest()
                        setBody(RefreshRequest(refresh))
                    }.body<AuthTokens>()
                    tokens.save(renewed)
                    BearerTokens(renewed.accessToken, renewed.refreshToken)
                } catch (e: Exception) {
                    tokens.clear()
                    null
                }
            }
            // 로그인/갱신 요청에는 토큰을 붙이지 않는다
            sendWithoutRequest { request -> !request.url.buildString().contains("api/auth/") }
        }
    }
    HttpResponseValidator {
        handleResponseExceptionWithRequest { cause, _ ->
            val response = (cause as? ResponseException)?.response ?: return@handleResponseExceptionWithRequest
            val error = runCatching { response.body<ErrorResponse>() }.getOrNull()
            val supportCode = error?.errorCode
            // 서버가 에러 코드를 만들어 기록한 500: 화면마다 처리하지 않아도 앱 루트가 안내 창을 띄운다
            if (supportCode != null) UnexpectedErrors.report(UnexpectedError(supportCode, error.reason ?: "CODE"))
            throw ApiException(
                status = response.status.value,
                code = error?.code ?: "UNKNOWN",
                message = error?.message ?: response.status.description,
                params = error?.params.orEmpty().mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: v.toString() },
                supportCode = supportCode,
                reason = error?.reason,
            )
        }
    }
}

/** 로그인/로그아웃 직후 Ktor 가 캐시한 토큰을 버리고 저장소에서 다시 읽게 한다 */
fun HttpClient.resetAuthTokens() {
    authProvider<BearerAuthProvider>()?.clearToken()
}
