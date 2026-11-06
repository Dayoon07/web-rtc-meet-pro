package com.webrtcmeetpro.feature.auth

/** 웹(apps/client/src/shared/config/countries.ts)과 같은 목록. 국가 이름은 화면에서 현재 언어로 표시 */
val DIAL_CODES: Map<String, String> = linkedMapOf(
    "KR" to "82", "US" to "1", "JP" to "81", "CN" to "86", "TW" to "886", "HK" to "852", "SG" to "65",
    "VN" to "84", "TH" to "66", "PH" to "63", "ID" to "62", "MY" to "60", "IN" to "91", "AU" to "61",
    "NZ" to "64", "CA" to "1", "GB" to "44", "DE" to "49", "FR" to "33", "ES" to "34", "IT" to "39",
    "NL" to "31", "SE" to "46", "BR" to "55", "MX" to "52",
)

const val MIN_AGE = 14

/** 국가번호 + 국내 번호 → E.164. 국내 번호 앞의 0 은 뗀다 (010 → 10) */
fun toE164(countryCode: String, local: String): String {
    val digits = local.filter(Char::isDigit).trimStart('0')
    val dial = DIAL_CODES[countryCode] ?: return ""
    return if (digits.isEmpty()) "" else "+$dial$digits"
}

/** 앱 언어 → 가입 기본 국가 */
fun guessCountry(languageTag: String): String =
    mapOf("ko" to "KR", "ja" to "JP", "zh" to "CN", "en" to "US")[languageTag.take(2)] ?: "KR"
