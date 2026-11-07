package com.webrtcmeetpro.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/*
 * 플랫폼마다 구현이 달라야 하는 것만 expect 로 둔다.
 * - LocalAppLocale: androidMain / desktopMain 각각 (Android 는 Configuration, Desktop 은 Locale.setDefault)
 * - 시간·국가 이름: jvmSharedMain 한 곳 (Android·Desktop 모두 java.time 사용)
 * 서버 주소·저장 경로·시스템 알림처럼 객체로 주입할 수 있는 건 expect 대신 플랫폼 Koin 모듈에서 넣는다.
 */

/** 앱 안에서 언어를 바꾸면 Compose 리소스가 그 언어로 다시 읽히게 한다. value 가 null 이면 기기 언어 */
expect object LocalAppLocale {
    val current: String @Composable get
    @Composable infix fun provides(value: String?): ProvidedValue<*>
}

/** "2026-11-06T10:00:00+09:00" → 기기 시간대·현재 언어로 "2026. 11. 6. 오전 10:00" 처럼 */
expect fun formatDateTime(iso: String): String

/** 채팅 메시지 옆 시각. 오늘이면 "오후 3:20", 아니면 "11. 5." */
expect fun formatChatTime(iso: String): String

/** 지금부터 iso 시각까지 남은 분 (지났으면 음수) */
expect fun minutesUntil(iso: String): Long

/**
 * DatePicker 가 주는 날짜(UTC 자정 millis) + 시·분(기기 시간대) → 서버로 보낼 오프셋 포함 ISO.
 * 서버는 오프셋을 보고 자기 시간대로 바꾼다.
 */
expect fun toOffsetIso(dateUtcMillis: Long, hour: Int, minute: Int): String

/** 서버 시각(ISO) → DatePicker·TimePicker 값 (날짜 UTC 자정 millis, 시, 분 — 기기 시간대) */
expect fun pickerParts(iso: String): Triple<Long, Int, Int>

/** 회의 예약 기본값: 다음 정각 (날짜 UTC 자정 millis, 시, 분) */
expect fun nextFullHour(): Triple<Long, Int, Int>

/** "KR" → 현재 언어로 "대한민국" / "South Korea" */
expect fun countryName(code: String): String

/** 올해 (출생년도 목록용) */
expect fun currentYear(): Int

/** 시스템 알림 (Android 알림 / Windows 트레이 풍선). 앱이 켜져 있는 동안만 */
interface SystemNotifier {
    fun show(title: String, body: String)
}
