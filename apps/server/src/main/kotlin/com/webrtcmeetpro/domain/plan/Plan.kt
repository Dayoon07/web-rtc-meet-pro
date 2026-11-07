package com.webrtcmeetpro.domain.plan

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.YesNoConverter

/**
 * 요금제 (FREE / PRO / TEAM / BUSINESS). 한도는 코드가 아니라 이 행에서 읽는다.
 * 금액·한도 변경은 plans 테이블 값만 바꾸면 된다. 연 결제 금액은 월 가격 × 12 × 0.9.
 * sort_order 가 클수록 높은 플랜 (구독이 여러 개면 가장 높은 것을 쓴다).
 */
@Entity
@Table(name = "plans")
class Plan(
    @Column(nullable = false, unique = true)
    val code: String,

    @Column(nullable = false)
    var name: String,

    var description: String?,

    @Column(nullable = false)
    var monthlyPrice: Long,

    @Column(nullable = false)
    var maxOwnedTeams: Int,

    @Column(nullable = false)
    var maxMembersPerTeam: Int,

    /** null = 제한 없음 */
    var maxGroupRoomsPerTeam: Int?,

    /** 회의 한 번에 함께 들어올 수 있는 사람 수 (팀 소유자 플랜 기준) */
    @Column(nullable = false)
    var maxMeetingParticipants: Int,

    @Convert(converter = YesNoConverter::class)
    @Column(nullable = false)
    var recordingEnabled: Boolean,

    /** 팀 소유자 한 명이 가진 모든 팀의 녹화 합계 용량 */
    @Column(nullable = false)
    var recordingStorageGb: Int = 0,

    /** 녹화 보관 일수. 지나면 자동으로 지운다 */
    @Column(nullable = false)
    var recordingRetentionDays: Int = 30,

    @Column(nullable = false)
    var sortOrder: Int = 0,

    @Convert(converter = YesNoConverter::class)
    @Column(nullable = false)
    var active: Boolean = true,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    var id: Long? = null
        protected set

    val isFree: Boolean get() = monthlyPrice == 0L

    fun canOwnMoreTeams(currentOwned: Long): Boolean = currentOwned < maxOwnedTeams
    fun canAddMember(currentMembers: Long): Boolean = currentMembers < maxMembersPerTeam
    fun canAddGroupRoom(currentChannels: Long): Boolean = maxGroupRoomsPerTeam?.let { currentChannels < it } ?: true
    fun canJoinMeeting(currentParticipants: Long): Boolean = currentParticipants < maxMeetingParticipants

    val recordingStorageBytes: Long get() = recordingStorageGb.toLong() * 1024 * 1024 * 1024

    fun price(cycle: BillingCycle): Long = when (cycle) {
        BillingCycle.MONTHLY -> monthlyPrice
        BillingCycle.YEARLY -> monthlyPrice * 12 * (100 - YEARLY_DISCOUNT_PERCENT) / 100
    }

    companion object {
        const val FREE_CODE = "FREE"
        const val YEARLY_DISCOUNT_PERCENT = 10L
    }
}

interface PlanRepository {
    fun findPlanById(id: Long): Plan?
    fun findByCode(code: String): Plan?
    fun findAllByActiveTrueOrderBySortOrderAsc(): List<Plan>
}
