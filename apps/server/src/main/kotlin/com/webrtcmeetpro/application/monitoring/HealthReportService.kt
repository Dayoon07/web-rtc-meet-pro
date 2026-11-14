package com.webrtcmeetpro.application.monitoring

import com.webrtcmeetpro.domain.monitoring.dto.res.HealthCheckResDto
import com.webrtcmeetpro.domain.monitoring.dto.res.HealthReportResDto
import com.webrtcmeetpro.application.mail.MailMessage
import com.webrtcmeetpro.application.mail.MailService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.lang.management.ManagementFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 서버 상태 보고 (12시간마다 메일, 스케줄러는 HealthReportScheduler).
 * 서버(JVM 메모리·가동 시간) · DB(응답·용량, Oracle Free 한도 12GB) · 미디어 사이드카(/health) · 서버 메모리·디스크 · 지난 12시간 오류 수.
 * 서버가 죽으면 이 메일 자체가 안 오므로, 서버가 죽었을 때 알림은 호스트의 deploy/watchdog.sh 가 따로 보낸다
 */
@Service
class HealthReportService(
    private val jdbc: JdbcTemplate,
    private val mail: MailService,
    private val json: JsonMapper,
    /** 받는 사람 (운영자). 바꾸려면 HEALTH_REPORT_TO */
    @Value("\${app.monitoring.report-to}") private val reportTo: String,
    /** 미디어 사이드카 상태 주소. 운영(도커)은 http://host.docker.internal:4000/health */
    @Value("\${app.monitoring.media-health-url}") private val mediaHealthUrl: String,
    /** 이 비율(%)을 넘으면 경고 */
    @Value("\${app.monitoring.disk-warn-percent:85}") private val diskWarnPercent: Int,
    @Value("\${app.monitoring.memory-warn-percent:90}") private val memoryWarnPercent: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun collect(now: LocalDateTime = LocalDateTime.now(SEOUL)): HealthReportResDto {
        val warnings = mutableListOf<String>()
        val checks = listOf(serverCheck(warnings), dbCheck(warnings), mediaCheck(), hostMemoryCheck(warnings), diskCheck(warnings), errorCheck(warnings))
        return HealthReportResDto(now, checks, warnings)
    }

    /** 지금 상태를 모아 운영자에게 메일로 보낸다. 보낸 보고를 돌려준다 */
    fun sendReport(): HealthReportResDto {
        val report = collect()
        mail.sendAfterCommit(toMail(report))
        log.info("상태 보고 메일 → {} ({})", reportTo, if (report.healthy) "정상" else "확인 필요")
        return report
    }

    fun toMail(report: HealthReportResDto): MailMessage {
        val time = report.at.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val subject = "[MQ 서버 상태] $time — " + if (report.healthy) "정상" else "확인 필요"
        val text = buildString {
            appendLine("MQ 서버 상태 보고 ($time, 한국 시간)")
            appendLine()
            report.checks.forEach { appendLine("${if (it.ok) "✅" else "❌"} ${it.name}: ${it.detail}") }
            if (report.warnings.isNotEmpty()) {
                appendLine()
                appendLine("⚠️ 경고")
                report.warnings.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("이 메일은 12시간마다(오전 9시·오후 9시) 옵니다. 서버가 꺼지면 이 메일 대신 watchdog 경고 메일이 옵니다.")
        }
        return MailMessage(reportTo, subject, text)
    }

    private fun serverCheck(warnings: MutableList<String>): HealthCheckResDto {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / MB
        val maxMb = rt.maxMemory() / MB
        val percent = if (maxMb > 0) usedMb * 100 / maxMb else 0
        if (percent >= memoryWarnPercent) warnings += "서버(JVM) 메모리 ${percent}% 사용 (${usedMb}MB / ${maxMb}MB)"
        val uptime = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().uptime)
        val threads = ManagementFactory.getThreadMXBean().threadCount
        return HealthCheckResDto("서버", true, "켜짐 · 가동 ${uptime.toDays()}일 ${uptime.toHoursPart()}시간 · 메모리(JVM) ${usedMb}MB / ${maxMb}MB (${percent}%) · 스레드 $threads")
    }

    private fun dbCheck(warnings: MutableList<String>): HealthCheckResDto = try {
        val t0 = System.nanoTime()
        jdbc.queryForObject("SELECT 1 FROM dual", Int::class.java)
        val ms = (System.nanoTime() - t0) / 1_000_000
        // 이 계정(스키마)의 데이터 용량. Oracle Free 는 사용자 데이터 12GB 가 한도
        val bytes = jdbc.queryForObject("SELECT NVL(SUM(bytes), 0) FROM user_segments", Long::class.java) ?: 0
        val mb = bytes / MB
        val percent = mb * 100 / ORACLE_FREE_LIMIT_MB
        if (percent >= diskWarnPercent) warnings += "DB 데이터 ${mb}MB — Oracle Free 한도 12GB 의 ${percent}%"
        HealthCheckResDto("DB", true, "응답 ${ms}ms · 데이터 ${mb}MB (Oracle Free 한도 12GB 의 ${percent}%)")
    } catch (e: Exception) {
        HealthCheckResDto("DB", false, "응답 없음 — ${e.javaClass.simpleName}: ${e.message?.take(200)}")
    }

    private fun mediaCheck(): HealthCheckResDto {
        if (mediaHealthUrl.isBlank()) return HealthCheckResDto("미디어 사이드카", true, "확인 안 함 (주소 없음)")
        return try {
            val res = http.send(HttpRequest.newBuilder(URI(mediaHealthUrl)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() != 200) return HealthCheckResDto("미디어 사이드카", false, "응답 코드 ${res.statusCode()}")
            val body = json.readTree(res.body())
            val parts = listOfNotNull(
                body.get("rooms")?.let { "회의방 ${it.asInt()}개" },
                body.get("peers")?.let { "참가자 ${it.asInt()}명" },
                body.get("workers")?.let { "워커 ${it.asInt()}개" },
                body.get("memoryMb")?.let { "메모리 ${it.asInt()}MB" },
            )
            HealthCheckResDto("미디어 사이드카", true, "켜짐 · " + parts.joinToString(" · "))
        } catch (e: Exception) {
            HealthCheckResDto("미디어 사이드카", false, "응답 없음 — ${e.javaClass.simpleName}: ${e.message?.take(200)}")
        }
    }

    /** 서버(호스트) 전체 메모리. 리눅스는 /proc/meminfo (도커 안에서도 호스트 값), 아니면 JVM 이 아는 값 */
    private fun hostMemoryCheck(warnings: MutableList<String>): HealthCheckResDto {
        val (totalMb, availableMb) = readMeminfo() ?: run {
            val os = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
                ?: return HealthCheckResDto("서버 메모리", true, "알 수 없음")
            os.totalMemorySize / MB to os.freeMemorySize / MB
        }
        val usedMb = totalMb - availableMb
        val percent = if (totalMb > 0) usedMb * 100 / totalMb else 0
        if (percent >= memoryWarnPercent) warnings += "서버 메모리 ${percent}% 사용 (남은 ${availableMb}MB)"
        return HealthCheckResDto("서버 메모리", true, "${usedMb}MB / ${totalMb}MB 사용 (${percent}%, 남은 ${availableMb}MB)")
    }

    private fun readMeminfo(): Pair<Long, Long>? {
        val file = File("/proc/meminfo").takeIf { it.canRead() } ?: return null
        val values = file.readLines().associate { line ->
            val (key, rest) = line.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            key.trim() to (rest.trim().substringBefore(' ').toLongOrNull() ?: 0L)
        }
        val total = values["MemTotal"] ?: return null
        val available = values["MemAvailable"] ?: return null
        return total / 1024 to available / 1024
    }

    /** 서버 디스크 (도커 안의 / 는 호스트 디스크 크기를 보여 준다) */
    private fun diskCheck(warnings: MutableList<String>): HealthCheckResDto {
        val root = File(File.listRoots().firstOrNull()?.path ?: "/")
        val totalGb = root.totalSpace / GB
        val freeGb = root.usableSpace / GB
        val usedGb = totalGb - freeGb
        val percent = if (root.totalSpace > 0) (root.totalSpace - root.usableSpace) * 100 / root.totalSpace else 0
        if (percent >= diskWarnPercent) warnings += "디스크 ${percent}% 사용 (남은 ${freeGb}GB)"
        return HealthCheckResDto("서버 디스크", true, "${usedGb}GB / ${totalGb}GB 사용 (${percent}%, 남은 ${freeGb}GB)")
    }

    /** 지난 12시간 동안 기록된 예상 못 한 오류 (관리자 사이트 → 에러 기록) */
    private fun errorCheck(warnings: MutableList<String>): HealthCheckResDto = try {
        // created_dt 는 서버 시간대로 저장되므로 서버 시간 기준으로 (표시만 한국 시간)
        val since = LocalDateTime.now().minusHours(12)
        val rows = jdbc.queryForList(
            "SELECT category, COUNT(*) AS cnt FROM admin_error_logs WHERE created_dt >= ? AND deleted_yn = 'N' GROUP BY category ORDER BY cnt DESC",
            since,
        )
        val total = rows.sumOf { (it["CNT"] as Number).toLong() }
        val byCategory = rows.joinToString(", ") { "${it["CATEGORY"]} ${(it["CNT"] as Number).toLong()}건" }
        if (total >= ERROR_WARN) warnings += "지난 12시간 오류 ${total}건 — 관리자 사이트 에러 기록 확인"
        HealthCheckResDto("지난 12시간 오류", true, if (total == 0L) "없음" else "${total}건 ($byCategory)")
    } catch (e: Exception) {
        HealthCheckResDto("지난 12시간 오류", false, "조회 실패 — ${e.javaClass.simpleName}")
    }

    companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private const val MB = 1024L * 1024
        private const val GB = MB * 1024
        private const val ORACLE_FREE_LIMIT_MB = 12L * 1024
        /** 12시간에 이 이상이면 경고 */
        private const val ERROR_WARN = 20
    }
}
