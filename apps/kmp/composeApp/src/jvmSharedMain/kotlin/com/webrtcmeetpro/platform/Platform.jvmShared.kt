package com.webrtcmeetpro.platform

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private fun parseLocal(iso: String) = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())

actual fun formatDateTime(iso: String): String = runCatching {
    parseLocal(iso).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()))
}.getOrDefault(iso)

actual fun formatChatTime(iso: String): String = runCatching {
    val time = parseLocal(iso)
    val locale = Locale.getDefault()
    if (time.toLocalDate() == LocalDate.now()) {
        time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    } else {
        time.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
    }
}.getOrDefault("")

actual fun minutesUntil(iso: String): Long = runCatching {
    ChronoUnit.MINUTES.between(Instant.now(), OffsetDateTime.parse(iso).toInstant())
}.getOrDefault(0)

actual fun toOffsetIso(dateUtcMillis: Long, hour: Int, minute: Int): String {
    val date = Instant.ofEpochMilli(dateUtcMillis).atOffset(ZoneOffset.UTC).toLocalDate()
    return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
        .atZone(ZoneId.systemDefault())
        .toOffsetDateTime()
        .toString()
}

actual fun pickerParts(iso: String): Triple<Long, Int, Int> = runCatching {
    val local = parseLocal(iso)
    Triple(local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), local.hour, local.minute)
}.getOrElse { nextFullHour() }

actual fun nextFullHour(): Triple<Long, Int, Int> {
    val next = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1)
    val dateMillis = next.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    return Triple(dateMillis, next.hour, 0)
}

actual fun countryName(code: String): String =
    Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault()).ifEmpty { code }

actual fun currentYear(): Int = LocalDate.now().year

// ---------------------------------------------------------------- 앱 오류 신고 (AppErrorReporter)

actual fun todayYmd(): String = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

actual fun installCrashHandler(onCrash: (Throwable) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
        runCatching { onCrash(e) }
        // 원래 처리기(Android 의 "앱이 중지됨" 등)를 그대로 부른다
        if (previous != null) previous.uncaughtException(thread, e) else e.printStackTrace()
    }
}

actual fun appendLine(path: String, line: String) {
    java.io.File(path).apply { parentFile?.mkdirs() }.appendText(line + "\n", Charsets.UTF_8)
}

actual fun readAndDeleteText(path: String): String? {
    val file = java.io.File(path)
    if (!file.exists()) return null
    return file.readText(Charsets.UTF_8).also { file.delete() }
}
