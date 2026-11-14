package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.domain.plan.AiFeature
import com.webrtcmeetpro.domain.plan.AiUsage
import com.webrtcmeetpro.domain.plan.AiUsageQueryRepository
import com.webrtcmeetpro.domain.plan.AiUsageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * AI 기능 한 달 사용 시간. 한도는 팀 소유자의 지금 플랜이고, 쓴 시간도 소유자의 모든 팀을 합친다 (plans.ai_notes_minutes / live_caption_minutes, 0 = 없음).
 * 달이 바뀌면 새로 센다.
 */
@Service
@Transactional(readOnly = true)
class AiQuotaService(
    private val usageQueries: AiUsageQueryRepository,
    private val usages: AiUsageRepository,
    private val planService: PlanService,
) {
    /** 플랜의 한 달 한도(초). 0 = 플랜에 없음 */
    fun limitSeconds(teamId: Long, feature: AiFeature, now: LocalDateTime = LocalDateTime.now()): Long {
        val plan = planService.planOfTeam(teamId, now)
        val minutes = when (feature) {
            AiFeature.NOTES -> plan.aiNotesMinutes
            AiFeature.CAPTIONS -> plan.liveCaptionMinutes
        }
        return minutes.toLong() * 60
    }

    /** 이번 달 쓴 시간(초): 플랜이 소유자 단위라 소유자의 모든 팀을 합친다 */
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
