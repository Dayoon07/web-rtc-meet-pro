package com.webrtcmeetpro.batch.plan

import com.webrtcmeetpro.application.meeting.RecordingService
import com.webrtcmeetpro.application.plan.BillingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 구독 갱신 (자동결제·만료·만료 3일 전 알림)과 오래된 녹화 정리.
 * 서버가 여러 대가 되면 ShedLock 등으로 한 대만 돌게 해야 한다 (두 대가 같은 구독을 동시에 결제하지 않게).
 */
@Component
class SubscriptionScheduler(private val billingService: BillingService, private val recordingService: RecordingService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.billing.renew-interval:10m}", initialDelayString = "30s")
    fun renew() {
        runCatching { billingService.processRenewals() }
            .onSuccess { if (it > 0) log.info("구독 갱신·만료 {}건 처리", it) }
            .onFailure { log.error("구독 갱신 실패", it) }
    }

    /** 플랜의 보관 일수가 지난 녹화를 지운다 (하루 한 번이면 충분) */
    @Scheduled(fixedDelayString = "\${app.billing.retention-interval:6h}", initialDelayString = "2m")
    fun purgeRecordings() {
        runCatching { recordingService.purgeExpired() }
            .onSuccess { if (it > 0) log.info("보관 기간이 지난 녹화 {}건 삭제", it) }
            .onFailure { log.error("오래된 녹화 정리 실패", it) }
    }
}
