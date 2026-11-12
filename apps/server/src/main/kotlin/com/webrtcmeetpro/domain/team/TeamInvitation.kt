package com.webrtcmeetpro.domain.team

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class InvitationStatus { PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELED }

@Entity
@Table(name = "team_invitations")
class TeamInvitation(
    @Column(nullable = false)
    val teamId: Long,

    @Column(nullable = false)
    val inviterId: Long,

    @Column(nullable = false)
    val inviteeEmail: String,

    /** 가입된 사용자면 채움 */
    val inviteeId: Long?,

    @Column(nullable = false, unique = true)
    val token: String,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invitation_id")
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: InvitationStatus = InvitationStatus.PENDING
        protected set

    var respondedAt: LocalDateTime? = null
        protected set

    fun isPending(now: LocalDateTime = LocalDateTime.now()) =
        status == InvitationStatus.PENDING && now.isBefore(expiresAt)

    fun accept(at: LocalDateTime = LocalDateTime.now()) = respond(InvitationStatus.ACCEPTED, at)
    fun decline(at: LocalDateTime = LocalDateTime.now()) = respond(InvitationStatus.DECLINED, at)
    fun cancel(at: LocalDateTime = LocalDateTime.now()) = respond(InvitationStatus.CANCELED, at)

    private fun respond(next: InvitationStatus, at: LocalDateTime) {
        check(isPending(at)) { "대기 중인 초대가 아닙니다." }
        status = next
        respondedAt = at
    }
}

interface TeamInvitationRepository {
    fun save(invitation: TeamInvitation): TeamInvitation
    fun findInvitationById(id: Long): TeamInvitation?
    fun findAllByTeamIdAndStatus(teamId: Long, status: InvitationStatus): List<TeamInvitation>
    fun findByToken(token: String): TeamInvitation?
    fun findAllByInviteeEmailAndStatus(inviteeEmail: String, status: InvitationStatus): List<TeamInvitation>
    fun countByTeamIdAndStatus(teamId: Long, status: InvitationStatus): Long
    fun findAllByTeamIdInAndStatus(teamIds: Collection<Long>, status: InvitationStatus): List<TeamInvitation>
}
