package com.webrtcmeetpro.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * 플랫폼마다 구현이 달라야 하는 것만 expect 로 둔다.
 * 서버 주소·저장 경로처럼 값만 다른 것은 expect 대신 플랫폼 Koin 모듈(androidMain/desktopMain)에서 넣는다.
 */

/**
 * 앱 안에서 언어를 바꾸면 Compose 리소스가 그 언어로 다시 읽히게 한다.
 * Android 는 Configuration 의 locale 을, Desktop(JVM)은 Locale.setDefault 를 바꾼다.
 * value 가 null 이면 기기 언어.
 */
expect object LocalAppLocale {
    val current: String @Composable get
    @Composable infix fun provides(value: String?): ProvidedValue<*>
}

/** "2026-11-06T10:00:00+09:00" → 기기 시간대·현재 언어로 "2026. 11. 6. 오전 10:00" 처럼 */
expect fun formatDateTime(iso: String): String
