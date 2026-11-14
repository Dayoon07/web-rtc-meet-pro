package com.webrtcmeetpro.domain.team

import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
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

/**
 * 어느 역할이 무엇을 할 수 있는지는 [TeamRole] 한 곳에서만 정한다.
 * 팀원이면 누구나 하는 일(채팅, 회의 예약·참여)과 자원과의 관계로 정해지는 일(회의 주최자의 자기 회의 취소)은 권한이 아니다.
 *
 * @param deniedError 권한이 없을 때 돌려줄 에러
 */
enum class TeamPermission(val deniedError: ErrorCode) {
    INVITE_MEMBER(ErrorCode.CANNOT_INVITE),

    /** 채널·카테고리 만들기·고치기 */
    CREATE_GROUP_ROOM(ErrorCode.CANNOT_MANAGE_CHANNELS),

    /** 다른 사람이 연 예약 회의 고치기·취소 */
    CANCEL_ANY_MEETING(ErrorCode.NOT_MEETING_HOST),

    /** 부적절한 메시지 정리 */
    DELETE_ANY_MESSAGE(ErrorCode.CANNOT_DELETE_MESSAGE),

    /** ADMIN 지정·해제 */
    MANAGE_ROLES(ErrorCode.NOT_TEAM_OWNER),

    /** ADMIN 은 일반 팀원만 내보낼 수 있다 (TeamMember.canRemove) */
    REMOVE_MEMBER(ErrorCode.CANNOT_REMOVE_MEMBER),

    DELETE_TEAM(ErrorCode.NOT_TEAM_OWNER),
}

/** 역할을 추가하거나 권한을 조정할 때는 여기만 바꾼다 */
enum class TeamRole(val permissions: Set<TeamPermission>) {
    /** 권한이 새로 생기면 자동으로 포함된다 */
    OWNER(TeamPermission.entries.toSet()),

    ADMIN(setOf(TeamPermission.INVITE_MEMBER, TeamPermission.CREATE_GROUP_ROOM, TeamPermission.CANCEL_ANY_MEETING, TeamPermission.REMOVE_MEMBER, TeamPermission.DELETE_ANY_MESSAGE)),

    MEMBER(emptySet()),
    ;

    fun can(permission: TeamPermission): Boolean = permission in permissions
}
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
) : BaseEntity() {
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

    /** OWNER 는 팀을 만든 사람 한 명뿐이라 주거나 뺏을 수 없다 */
    fun changeRole(next: TeamRole) {
        if (role == TeamRole.OWNER || next == TeamRole.OWNER) throw BusinessException(ErrorCode.OWNER_ROLE_FIXED)
        role = next
    }

    /** OWNER·자기 자신은 안 되고, ADMIN 은 일반 팀원만 */
    fun canRemove(target: TeamMember): Boolean =
        can(TeamPermission.REMOVE_MEMBER) && target.userId != userId && target.role != TeamRole.OWNER &&
            (role == TeamRole.OWNER || target.role == TeamRole.MEMBER)

    fun can(permission: TeamPermission): Boolean = status == TeamMemberStatus.ACTIVE && role.can(permission)

    fun leave(at: LocalDateTime = LocalDateTime.now()) {
        status = TeamMemberStatus.LEFT
        leftAt = at
    }

    fun kick(at: LocalDateTime = LocalDateTime.now()) {
        status = TeamMemberStatus.KICKED
        leftAt = at
    }

    /** (team_id, user_id) 가 유니크라 나갔던 팀에 다시 들어오면 행을 재사용한다 */
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
    fun findAllByTeamIdInAndStatus(teamIds: Collection<Long>, status: TeamMemberStatus): List<TeamMember>
}
