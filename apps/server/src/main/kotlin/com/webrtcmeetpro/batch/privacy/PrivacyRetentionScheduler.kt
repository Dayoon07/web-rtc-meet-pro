package com.webrtcmeetpro.batch.privacy

import com.webrtcmeetpro.application.privacy.PrivacyRetentionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 하루에 한 번 보관 기간이 지난 개인정보 파기 (새벽 4시) */
@Component
class PrivacyRetentionScheduler(private val retention: PrivacyRetentionService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.privacy.purge-cron:0 0 4 * * *}")
    fun purge() {
        runCatching { retention.purge() }.onFailure { log.error("개인정보 파기 실패", it) }
    }
}
