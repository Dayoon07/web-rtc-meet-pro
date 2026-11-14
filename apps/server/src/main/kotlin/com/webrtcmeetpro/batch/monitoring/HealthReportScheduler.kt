package com.webrtcmeetpro.batch.monitoring

import com.webrtcmeetpro.application.monitoring.HealthReportService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class HealthReportScheduler(private val reports: HealthReportService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.monitoring.report-cron:0 0 9,21 * * *}", zone = "Asia/Seoul")
    fun report() {
        runCatching { reports.sendReport() }.onFailure { log.error("상태 보고 메일 실패", it) }
    }
}
