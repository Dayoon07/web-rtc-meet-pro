package com.webrtcmeetpro.core.data

import com.webrtcmeetpro.core.model.Me
import com.webrtcmeetpro.core.model.Meeting
import com.webrtcmeetpro.core.model.TeamSummary
import com.webrtcmeetpro.core.network.LimeetApi
import kotlinx.coroutines.flow.Flow

class AuthRepository(
    private val api: LimeetApi,
    private val session: SessionStore,
) {
    val isLoggedIn: Flow<Boolean> = session.isLoggedIn

    suspend fun login(email: String, password: String) {
        val tokens = api.login(email.trim(), password)
        session.save(tokens)
        api.resetAuth()
    }

    /** 서버 세션 폐기가 실패해도(오프라인 등) 기기에서는 로그아웃시킨다 */
    suspend fun logout() {
        val refresh = session.load()?.refreshToken
        if (refresh != null) runCatching { api.logout(refresh) }
        session.clear()
        api.resetAuth()
    }
}

class UserRepository(private val api: LimeetApi) {
    suspend fun me(): Me = api.me()
}

class TeamRepository(private val api: LimeetApi) {
    suspend fun teams(): List<TeamSummary> = api.teams()
}

class MeetingRepository(private val api: LimeetApi) {
    suspend fun upcoming(): List<Meeting> = api.meetings(past = false)
    suspend fun past(): List<Meeting> = api.meetings(past = true)
}
