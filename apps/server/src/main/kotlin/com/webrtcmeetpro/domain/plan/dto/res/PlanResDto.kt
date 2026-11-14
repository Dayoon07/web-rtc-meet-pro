package com.webrtcmeetpro.domain.plan.dto.res

import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.Plan

data class PlanResDto(
    val code: String,
    val name: String,
    val description: String?,
    val monthlyPrice: Long,
    /** 연 결제 금액 (월 가격 × 12 × 0.9) */
    val yearlyPrice: Long,
    val maxMembersPerTeam: Int,
    /** 워크스페이스당 채널 수. null = 제한 없음 */
    val maxGroupRoomsPerTeam: Int?,
    val maxMeetingParticipants: Int,
    val recordingEnabled: Boolean,
    val recordingStorageGb: Int,
    val recordingRetentionDays: Int,
    /** 한 달 AI 회의록 시간(분). 0 = 없음 */
    val aiNotesMinutes: Int,
    /** 한 달 실시간 자막·번역 시간(분). 0 = 없음 */
    val liveCaptionMinutes: Int,
    /** 멤버 한도를 넘은 1명당 한 달 요금 (소유자가 허용했을 때) */
    val overagePricePerMember: Long,
    /** 클수록 높은 플랜 */
    val rank: Int,
) {
    companion object {
        fun from(plan: Plan) = PlanResDto(
            code = plan.code,
            name = plan.name,
            description = plan.description,
            monthlyPrice = plan.monthlyPrice,
            yearlyPrice = plan.price(BillingCycle.YEARLY),
            maxMembersPerTeam = plan.maxMembersPerTeam,
            maxGroupRoomsPerTeam = plan.maxGroupRoomsPerTeam,
            maxMeetingParticipants = plan.maxMeetingParticipants,
            recordingEnabled = plan.recordingEnabled,
            recordingStorageGb = plan.recordingStorageGb,
            recordingRetentionDays = plan.recordingRetentionDays,
            aiNotesMinutes = plan.aiNotesMinutes,
            liveCaptionMinutes = plan.liveCaptionMinutes,
            overagePricePerMember = plan.overagePricePerMember,
            rank = plan.sortOrder,
        )
    }
}
