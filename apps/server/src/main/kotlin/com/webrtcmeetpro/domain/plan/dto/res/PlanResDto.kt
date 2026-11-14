package com.webrtcmeetpro.domain.plan.dto.res

import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.Plan

data class PlanResDto(
    val code: String,
    val name: String,
    val description: String?,
    val monthlyPrice: Long,
    /** 월 가격 × 12 × 0.9 */
    val yearlyPrice: Long,
    val maxMembersPerTeam: Int,
    /** null = 제한 없음 */
    val maxGroupRoomsPerTeam: Int?,
    val maxMeetingParticipants: Int,
    val recordingEnabled: Boolean,
    val recordingStorageGb: Int,
    val recordingRetentionDays: Int,
    /** 0 = 없음 */
    val aiNotesMinutes: Int,
    /** 0 = 없음 */
    val liveCaptionMinutes: Int,
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
