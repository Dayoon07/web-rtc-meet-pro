package com.webrtcmeetpro.batch.privacy

import com.webrtcmeetpro.application.privacy.PrivacyRetentionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PrivacyRetentionScheduler(private val retention: PrivacyRetentionService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.privacy.purge-cron:0 0 4 * * *}")
    fun purge() {
        runCatching { retention.purge() }.onFailure { log.error("개인정보 파기 실패", it) }
    }
}
