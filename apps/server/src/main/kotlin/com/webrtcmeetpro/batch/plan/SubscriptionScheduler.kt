package com.webrtcmeetpro.batch.plan

import com.webrtcmeetpro.application.meeting.RecordingService
import com.webrtcmeetpro.application.plan.BillingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 서버가 여러 대가 되면 ShedLock 등으로 한 대만 돌게 해야 같은 구독을 두 번 결제하지 않는다 */
@Component
class SubscriptionScheduler(private val billingService: BillingService, private val recordingService: RecordingService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.billing.renew-interval:10m}", initialDelayString = "30s")
    fun renew() {
        runCatching { billingService.processRenewals() }
            .onSuccess { if (it > 0) log.info("구독 갱신·만료 {}건 처리", it) }
            .onFailure { log.error("구독 갱신 실패", it) }
        runCatching { billingService.processOverage() }
            .onSuccess { if (it > 0) log.info("초과 인원 요금 {}건 결제 시도", it) }
            .onFailure { log.error("초과 인원 요금 처리 실패", it) }
    }

    /** 보관 일수 단위라 자주 돌 필요가 없다 */
    @Scheduled(fixedDelayString = "\${app.billing.retention-interval:6h}", initialDelayString = "2m")
    fun purgeRecordings() {
        runCatching { recordingService.purgeExpired() }
            .onSuccess { if (it > 0) log.info("보관 기간이 지난 녹화 {}건 삭제", it) }
            .onFailure { log.error("오래된 녹화 정리 실패", it) }
    }
}
