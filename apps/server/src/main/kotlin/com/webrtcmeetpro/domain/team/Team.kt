package com.webrtcmeetpro.domain.team

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import org.hibernate.type.YesNoConverter
import java.time.LocalDateTime
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

enum class TeamStatus { ACTIVE, DELETED }

@Entity
@Table(name = "teams")
class Team(
    @Column(nullable = false)
    val ownerId: Long,

    @Column(nullable = false)
    var name: String,

    var description: String? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_id")
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TeamStatus = TeamStatus.ACTIVE
        protected set

    /** 1명당 월 초과 요금에 동의했다는 뜻이다 */
    @Convert(converter = YesNoConverter::class)
    @Column(nullable = false)
    var overageAllowed: Boolean = false
        protected set

    var overageAgreedAt: LocalDateTime? = null
        protected set

    var overageAgreedBy: Long? = null
        protected set

    /** 경고 메일을 하루 한 번까지만 보내려고 */
    var overageWarnedAt: LocalDateTime? = null
        protected set

    /** 월 자동결제 플랜이면 갱신 때 같이 내서 null */
    var overageNextChargeAt: LocalDateTime? = null
        protected set

    /** 같은 결제일에 "3일 뒤 결제" 메일을 두 번 보내지 않게 */
    var overageNoticeFor: LocalDateTime? = null
        protected set

    @Column(nullable = false)
    var overageFailedAttempts: Int = 0
        protected set

    /** 동의 시각과 사람을 남긴다 */
    fun allowOverage(ownerId: Long, at: LocalDateTime = LocalDateTime.now()) {
        overageAllowed = true
        overageAgreedAt = at
        overageAgreedBy = ownerId
        overageFailedAttempts = 0
    }

    /** 초과한 멤버가 없는지는 서비스가 확인한다 */
    fun disallowOverage() {
        overageAllowed = false
        overageNextChargeAt = null
        overageNoticeFor = null
    }

    fun shouldWarnOverage(now: LocalDateTime = LocalDateTime.now()): Boolean =
        overageWarnedAt?.isAfter(now.minusDays(1)) != true

    fun overageWarned(at: LocalDateTime = LocalDateTime.now()) {
        overageWarnedAt = at
    }

    /** 이미 있으면 그대로 */
    fun scheduleOverage(at: LocalDateTime) {
        if (overageNextChargeAt == null) overageNextChargeAt = at
    }

    fun clearOverageSchedule() {
        overageNextChargeAt = null
        overageFailedAttempts = 0
    }

    fun overageNoticeSent(forChargeAt: LocalDateTime) {
        overageNoticeFor = forChargeAt
    }

    fun overageCharged() {
        overageNextChargeAt = overageNextChargeAt?.plusMonths(1)
        overageFailedAttempts = 0
    }

    fun overageChargeFailed(now: LocalDateTime = LocalDateTime.now()) {
        overageFailedAttempts += 1
        overageNextChargeAt = now.plusDays(1)
    }

    /** 소유자의 다른 팀이 지워지면 그 팀의 한도 초과 설정·결제 일정을 이어받는다 */
    fun takeOverOverage(from: Team) {
        overageAllowed = from.overageAllowed
        overageAgreedAt = from.overageAgreedAt
        overageAgreedBy = from.overageAgreedBy
        overageWarnedAt = from.overageWarnedAt
        overageNextChargeAt = from.overageNextChargeAt
        overageNoticeFor = from.overageNoticeFor
        overageFailedAttempts = from.overageFailedAttempts
    }

    /** 행은 남긴다 (삭제 시각은 edited_dt) */
    fun delete() {
        status = TeamStatus.DELETED
        disallowOverage()
        markDeleted()
    }

    companion object {
        /** 넘으면 한도 초과 허용을 끈다. 새 초대만 막히고 있던 멤버는 그대로 */
        const val MAX_OVERAGE_ATTEMPTS = 3
    }
}

interface TeamRepository {
    fun save(team: Team): Team
    fun findTeamById(id: Long): Team?
    fun countByOwnerIdAndStatus(ownerId: Long, status: TeamStatus): Long
    /** 플랜·인원 한도는 소유자의 팀들을 합쳐서 본다 */
    fun findAllByOwnerIdAndStatus(ownerId: Long, status: TeamStatus): List<Team>
    fun findAllByIdInAndStatus(ids: Collection<Long>, status: TeamStatus): List<Team>
    fun findAllByOverageAllowedTrueAndStatus(status: TeamStatus): List<Team>
}
