package com.webrtcmeetpro.domain.plan

import com.webrtcmeetpro.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.YesNoConverter

/**
 * 요금제. 팀 개수 / 팀원 수 / 채널 수 한도를 코드가 아니라 이 행에서 읽는다.
 * 금액·한도 변경은 plans 테이블 값만 바꾸면 된다.
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

    @Column(nullable = false)
    var maxGroupRoomsPerTeam: Int,

    @Convert(converter = YesNoConverter::class)
    @Column(nullable = false)
    var recordingEnabled: Boolean,

    @Column(nullable = false)
    var sortOrder: Int = 0,

    @Convert(converter = YesNoConverter::class)
    @Column(nullable = false)
    var active: Boolean = true,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    var id: Long? = null
        protected set

    val isFree: Boolean get() = monthlyPrice == 0L

    fun canOwnMoreTeams(currentOwned: Long): Boolean = currentOwned < maxOwnedTeams
    fun canAddMember(currentMembers: Long): Boolean = currentMembers < maxMembersPerTeam
    fun canAddGroupRoom(currentChannels: Long): Boolean = currentChannels < maxGroupRoomsPerTeam

    companion object {
        const val FREE_CODE = "FREE"
    }
}

interface PlanRepository {
    fun findPlanById(id: Long): Plan?
    fun findByCode(code: String): Plan?
    fun findAllByActiveTrueOrderBySortOrderAsc(): List<Plan>
}
