package com.webrtcmeetpro.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.webrtcmeetpro.core.model.AuthTokens
import com.webrtcmeetpro.core.network.TokenStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/** 파일 위치만 플랫폼이 정한다 (Android: filesDir, Desktop: %APPDATA%/LimeetPro) */
fun createPreferencesDataStore(filePath: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { filePath.toPath() })

const val PREFERENCES_FILE_NAME = "limeet.preferences_pb"

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 지원 언어. null 이면 기기 언어를 따른다 */
enum class AppLanguage(val tag: String) { KO("ko"), EN("en"), JA("ja"), ZH("zh") }

data class AppSettings(val theme: ThemeMode = ThemeMode.SYSTEM, val language: AppLanguage? = null)

/** 로그인 토큰 저장소. Ktor Auth 플러그인이 TokenStorage 로 읽고 쓴다 */
class SessionStore(private val store: DataStore<Preferences>) : TokenStorage {

    /** 로그인 여부. 토큰이 지워지면(갱신 실패·로그아웃) false 가 되어 앱이 로그인 화면으로 간다 */
    val isLoggedIn: Flow<Boolean> = store.data.map { it[REFRESH] != null }

    override suspend fun load(): AuthTokens? {
        val prefs = store.data.first()
        val access = prefs[ACCESS] ?: return null
        val refresh = prefs[REFRESH] ?: return null
        return AuthTokens(access, refresh, prefs[EXPIRES_IN] ?: 0)
    }

    override suspend fun save(tokens: AuthTokens) {
        store.edit {
            it[ACCESS] = tokens.accessToken
            it[REFRESH] = tokens.refreshToken
            it[EXPIRES_IN] = tokens.expiresIn
        }
    }

    override suspend fun clear() {
        store.edit {
            it.remove(ACCESS)
            it.remove(REFRESH)
            it.remove(EXPIRES_IN)
        }
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES_IN = longPreferencesKey("expires_in")
    }
}

/** 테마·언어 설정 */
class SettingsStore(private val store: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            theme = prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            language = prefs[LANGUAGE]?.let { tag -> AppLanguage.entries.firstOrNull { it.tag == tag } },
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage?) {
        store.edit { if (language == null) it.remove(LANGUAGE) else it[LANGUAGE] = language.tag }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
    }
}
