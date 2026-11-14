package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.domain.plan.AiFeature
import com.webrtcmeetpro.domain.plan.AiUsage
import com.webrtcmeetpro.domain.plan.AiUsageQueryRepository
import com.webrtcmeetpro.domain.plan.AiUsageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** AI 기능 월 사용 시간. 플랜이 소유자 단위라 한도와 사용량 모두 소유자의 모든 팀을 합쳐 계산한다 */
@Service
@Transactional(readOnly = true)
class AiQuotaService(
    private val usageQueries: AiUsageQueryRepository,
    private val usages: AiUsageRepository,
    private val planService: PlanService,
) {
    /** 0 = 플랜에 없는 기능 */
    fun limitSeconds(teamId: Long, feature: AiFeature, now: LocalDateTime = LocalDateTime.now()): Long {
        val plan = planService.planOfTeam(teamId, now)
        val minutes = when (feature) {
            AiFeature.NOTES -> plan.aiNotesMinutes
            AiFeature.CAPTIONS -> plan.liveCaptionMinutes
        }
        return minutes.toLong() * 60
    }

    fun usedSeconds(teamId: Long, feature: AiFeature, now: LocalDateTime = LocalDateTime.now()): Long =
        planService.siblingTeamIds(teamId).takeIf { it.isNotEmpty() }?.let { usageQueries.sumUsedSeconds(it, feature, AiUsage.monthOf(now)) } ?: 0

    @Transactional
    fun add(teamId: Long, feature: AiFeature, seconds: Long, now: LocalDateTime = LocalDateTime.now()) {
        if (seconds <= 0) return
        val month = AiUsage.monthOf(now)
        val usage = usageQueries.findUsageId(teamId, feature, month)?.let(usages::findUsageById)
            ?: usages.save(AiUsage(teamId, feature, month))
        usage.add(seconds)
    }
}
