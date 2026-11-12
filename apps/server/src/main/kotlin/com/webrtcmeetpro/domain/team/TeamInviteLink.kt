package com.webrtcmeetpro.domain.team

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 팀 초대 링크. 링크(token)를 받은 사람은 누구든 팀에 들어올 수 있다 (비밀번호를 걸었으면 비밀번호도 맞혀야).
 * 기간 제한은 없고, 팀 소유자·관리자가 만들고 지운다 (지우면 그 링크로는 더 못 들어온다)
 */
@Entity
@Table(name = "team_invite_links")
class TeamInviteLink(
    @Column(nullable = false)
    val teamId: Long,

    /** 주소에 들어가는 값 (추측할 수 없게 무작위 32자) */
    @Column(nullable = false, unique = true)
    val token: String,

    /** 비밀번호 (BCrypt). null = 링크만 있으면 들어올 수 있다 */
    val passwordHash: String?,

    /** 만든 사람 (팀 소유자·관리자) */
    @Column(nullable = false)
    val createdBy: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invite_link_id")
    var id: Long? = null
        protected set

    /** 이 링크로 들어온 사람 수 */
    @Column(nullable = false)
    var joinCount: Int = 0
        protected set

    fun hasPassword(): Boolean = passwordHash != null

    fun joined() {
        joinCount += 1
    }

    /** 지우기: 행은 남기고 deleted_yn = 'Y' (그 링크로는 더 못 들어온다) */
    fun delete() = markDeleted()
}

interface TeamInviteLinkRepository {
    fun save(link: TeamInviteLink): TeamInviteLink
    fun findInviteLinkById(id: Long): TeamInviteLink?
    fun findByTokenAndDeletedFalse(token: String): TeamInviteLink?
    fun findAllByTeamIdAndDeletedFalseOrderByIdDesc(teamId: Long): List<TeamInviteLink>
}
