package com.webrtcmeetpro.domain.team

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class TeamRole { OWNER, ADMIN, MEMBER }
enum class TeamMemberStatus { ACTIVE, LEFT, KICKED }

@Entity
@Table(name = "team_members")
class TeamMember(
    @Column(nullable = false)
    val teamId: Long,

    @Column(nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: TeamRole,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_member_id")
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TeamMemberStatus = TeamMemberStatus.ACTIVE
        protected set

    @Column(nullable = false)
    var joinedAt: LocalDateTime = LocalDateTime.now()
        protected set

    var leftAt: LocalDateTime? = null
        protected set

    val canInvite: Boolean get() = status == TeamMemberStatus.ACTIVE && role != TeamRole.MEMBER

    fun leave(at: LocalDateTime = LocalDateTime.now()) {
        status = TeamMemberStatus.LEFT
        leftAt = at
    }

    fun kick(at: LocalDateTime = LocalDateTime.now()) {
        status = TeamMemberStatus.KICKED
        leftAt = at
    }

    /** 나갔던 팀에 다시 초대받아 들어올 때 (team_id, user_id 유니크라 행을 재사용) */
    fun rejoin(at: LocalDateTime = LocalDateTime.now()) {
        status = TeamMemberStatus.ACTIVE
        role = TeamRole.MEMBER
        joinedAt = at
        leftAt = null
    }
}

interface TeamMemberRepository {
    fun save(member: TeamMember): TeamMember
    fun findByTeamIdAndUserId(teamId: Long, userId: Long): TeamMember?
    fun findAllByTeamIdAndStatus(teamId: Long, status: TeamMemberStatus): List<TeamMember>
    fun findAllByUserIdAndStatus(userId: Long, status: TeamMemberStatus): List<TeamMember>
    fun countByTeamIdAndStatus(teamId: Long, status: TeamMemberStatus): Long
}
