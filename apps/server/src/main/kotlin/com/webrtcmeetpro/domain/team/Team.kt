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

    fun isOwnedBy(userId: Long) = ownerId == userId

    /** 삭제: 행은 남기고 status = DELETED + deleted_yn = 'Y' (삭제 시각은 edited_dt) */
    fun delete() {
        status = TeamStatus.DELETED
        markDeleted()
    }
}

interface TeamRepository {
    fun save(team: Team): Team
    fun findTeamById(id: Long): Team?
    fun countByOwnerIdAndStatus(ownerId: Long, status: TeamStatus): Long
    fun findAllByIdInAndStatus(ids: Collection<Long>, status: TeamStatus): List<Team>
}
