package com.webrtcmeetpro.domain.team

import com.webrtcmeetpro.domain.common.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class TeamStatus { ACTIVE, DELETED }

@Entity
@Table(name = "teams")
class Team(
    @Column(nullable = false)
    val ownerId: Long,

    @Column(nullable = false)
    var name: String,

    var description: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_id")
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TeamStatus = TeamStatus.ACTIVE
        protected set

    var deletedAt: LocalDateTime? = null
        protected set

    fun isOwnedBy(userId: Long) = ownerId == userId

    fun delete(at: LocalDateTime = LocalDateTime.now()) {
        status = TeamStatus.DELETED
        deletedAt = at
    }
}

interface TeamRepository {
    fun save(team: Team): Team
    fun findTeamById(id: Long): Team?
    fun countByOwnerIdAndStatus(ownerId: Long, status: TeamStatus): Long
    fun findAllByIdInAndStatus(ids: Collection<Long>, status: TeamStatus): List<Team>
}
