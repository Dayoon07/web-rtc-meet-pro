package com.webrtcmeetpro.domain.team

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 링크를 받은 사람은 누구든 들어올 수 있다. 기간 제한이 없어서 원치 않으면 지워야 한다 */
@Entity
@Table(name = "team_invite_links")
class TeamInviteLink(
    @Column(nullable = false)
    val teamId: Long,

    /** 추측할 수 없게 무작위 32자 */
    @Column(nullable = false, unique = true)
    val token: String,

    /** null = 링크만 있으면 들어올 수 있다 */
    val passwordHash: String?,

    @Column(nullable = false)
    val createdBy: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invite_link_id")
    var id: Long? = null
        protected set

    @Column(nullable = false)
    var joinCount: Int = 0
        protected set

    fun hasPassword(): Boolean = passwordHash != null

    fun joined() {
        joinCount += 1
    }

    /** 이미 들어온 사람은 그대로 */
    fun delete() = markDeleted()
}

interface TeamInviteLinkRepository {
    fun save(link: TeamInviteLink): TeamInviteLink
    fun findInviteLinkById(id: Long): TeamInviteLink?
    fun findByTokenAndDeletedFalse(token: String): TeamInviteLink?
    fun findAllByTeamIdAndDeletedFalseOrderByIdDesc(teamId: Long): List<TeamInviteLink>
}
