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

    // ---------------------------------------------------------- 멤버 초과 요금
    /** 소유자가 플랜 멤버 한도를 넘는 초대를 허용했는지 (= 1명당 월 초과 요금에 동의) */
    @Convert(converter = YesNoConverter::class)
    @Column(nullable = false)
    var overageAllowed: Boolean = false
        protected set

    var overageAgreedAt: LocalDateTime? = null
        protected set

    var overageAgreedBy: Long? = null
        protected set

    /** 마지막 한도 초과 경고 메일 (하루 한 번까지) */
    var overageWarnedAt: LocalDateTime? = null
        protected set

    /** 초과 요금을 따로 결제할 날 (월 자동결제 플랜이면 갱신 때 같이 내서 null) */
    var overageNextChargeAt: LocalDateTime? = null
        protected set

    /** "3일 뒤 결제" 메일을 보낸 결제일 */
    var overageNoticeFor: LocalDateTime? = null
        protected set

    @Column(nullable = false)
    var overageFailedAttempts: Int = 0
        protected set

    /** 소유자가 동의하고 켬. 동의 시각·사람을 남긴다 */
    fun allowOverage(ownerId: Long, at: LocalDateTime = LocalDateTime.now()) {
        overageAllowed = true
        overageAgreedAt = at
        overageAgreedBy = ownerId
        overageFailedAttempts = 0
    }

    /** 끔 (초과한 멤버가 없을 때만 — 서비스가 확인). 결제 일정도 지운다 */
    fun disallowOverage() {
        overageAllowed = false
        overageNextChargeAt = null
        overageNoticeFor = null
    }

    /** @return 경고 메일을 보내도 되면 true (하루에 한 번) */
    fun shouldWarnOverage(now: LocalDateTime = LocalDateTime.now()): Boolean =
        overageWarnedAt?.isAfter(now.minusDays(1)) != true

    fun overageWarned(at: LocalDateTime = LocalDateTime.now()) {
        overageWarnedAt = at
    }

    /** 따로 결제할 날 정하기 (이미 있으면 그대로) */
    fun scheduleOverage(at: LocalDateTime) {
        if (overageNextChargeAt == null) overageNextChargeAt = at
    }

    /** 따로 결제할 일이 없어짐 (초과 인원이 없거나, 월 자동결제 갱신 때 같이 냄) */
    fun clearOverageSchedule() {
        overageNextChargeAt = null
        overageFailedAttempts = 0
    }

    fun overageNoticeSent(forChargeAt: LocalDateTime) {
        overageNoticeFor = forChargeAt
    }

    /** 따로 결제 성공 → 다음 달 같은 날 */
    fun overageCharged() {
        overageNextChargeAt = overageNextChargeAt?.plusMonths(1)
        overageFailedAttempts = 0
    }

    /** 따로 결제 실패 → 하루 뒤 다시 */
    fun overageChargeFailed(now: LocalDateTime = LocalDateTime.now()) {
        overageFailedAttempts += 1
        overageNextChargeAt = now.plusDays(1)
    }

    /** 소유자의 다른 팀이 지워질 때: 그 팀이 들고 있던 한도 초과 설정·결제 일정을 이어받는다 */
    fun takeOverOverage(from: Team) {
        overageAllowed = from.overageAllowed
        overageAgreedAt = from.overageAgreedAt
        overageAgreedBy = from.overageAgreedBy
        overageWarnedAt = from.overageWarnedAt
        overageNextChargeAt = from.overageNextChargeAt
        overageNoticeFor = from.overageNoticeFor
        overageFailedAttempts = from.overageFailedAttempts
    }

    /** 삭제: 행은 남기고 status = DELETED + deleted_yn = 'Y' (삭제 시각은 edited_dt) */
    fun delete() {
        status = TeamStatus.DELETED
        disallowOverage()
        markDeleted()
    }

    companion object {
        /** 초과 요금 결제를 이만큼 실패하면 한도 초과 허용을 끈다 (새 초대만 막고, 있던 멤버는 그대로) */
        const val MAX_OVERAGE_ATTEMPTS = 3
    }
}

interface TeamRepository {
    fun save(team: Team): Team
    fun findTeamById(id: Long): Team?
    fun countByOwnerIdAndStatus(ownerId: Long, status: TeamStatus): Long
    /** 소유자(회사 계정)가 만든 팀 — 플랜·인원 한도는 이 팀들을 합쳐서 본다 */
    fun findAllByOwnerIdAndStatus(ownerId: Long, status: TeamStatus): List<Team>
    fun findAllByIdInAndStatus(ids: Collection<Long>, status: TeamStatus): List<Team>
    /** 한도 초과를 허용한 워크스페이스 (초과 요금 스케줄러) */
    fun findAllByOverageAllowedTrueAndStatus(status: TeamStatus): List<Team>
}
