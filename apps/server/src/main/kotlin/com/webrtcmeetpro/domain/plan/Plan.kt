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
 * 한도는 코드가 아니라 이 행에서 읽으므로 금액·한도는 plans 값만 바꾸면 된다.
 * sort_order 가 클수록 높은 플랜이고, 구독이 여러 개면 가장 높은 것을 쓴다.
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

    @Column(nullable = false)
    var maxMeetingParticipants: Int,

    @Convert(converter = YesNoConverter::class)
    @Column(nullable = false)
    var recordingEnabled: Boolean,

    /** 소유자의 모든 팀을 합친 용량 */
    @Column(nullable = false)
    var recordingStorageGb: Int = 0,

    @Column(nullable = false)
    var recordingRetentionDays: Int = 30,

    /** 0 = 플랜에 없음 */
    @Column(nullable = false)
    var aiNotesMinutes: Int = 0,

    /** 0 = 플랜에 없음 */
    @Column(nullable = false)
    var liveCaptionMinutes: Int = 0,

    @Column(nullable = false)
    var overagePricePerMember: Long = 9900,

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
