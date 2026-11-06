package com.webrtcmeetpro.core.network

import com.webrtcmeetpro.core.model.AuthTokens
import com.webrtcmeetpro.core.model.LoginRequest
import com.webrtcmeetpro.core.model.Me
import com.webrtcmeetpro.core.model.Meeting
import com.webrtcmeetpro.core.model.RefreshRequest
import com.webrtcmeetpro.core.model.Subscription
import com.webrtcmeetpro.core.model.TeamSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

/**
 * 서버 REST API (apps/server 의 presentation 컨트롤러와 1:1).
 * 화면이 늘어나면 도메인별로 나누면 되지만, 지금은 얇은 호출뿐이라 한 곳에 둔다.
 */
class LimeetApi(private val client: HttpClient) {

    // auth
    suspend fun login(email: String, password: String): AuthTokens =
        client.post("api/auth/login") { setBody(LoginRequest(email, password)) }.body()

    suspend fun logout(refreshToken: String) {
        client.post("api/auth/logout") { setBody(RefreshRequest(refreshToken)) }
    }

    // user / plan
    suspend fun me(): Me = client.get("api/users/me").body()

    suspend fun mySubscription(): Subscription = client.get("api/subscriptions/me").body()

    // team
    suspend fun teams(): List<TeamSummary> = client.get("api/teams").body()

    // meeting
    suspend fun meetings(past: Boolean = false): List<Meeting> =
        client.get("api/meetings") { parameter("scope", if (past) "past" else "upcoming") }.body()

    /** 로그인·로그아웃 뒤 캐시된 토큰을 비운다 */
    fun resetAuth() = client.resetAuthTokens()
}
