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
 * 팀 안에서 역할에 따라 할 수 있는 일. 어느 역할이 무엇을 할 수 있는지는 [TeamRole] 한 곳에서만 정하고,
 * 서비스는 TeamAccess.requirePermission(팀, 사용자, 권한) 한 줄로 확인한다.
 *
 * 권한이 아닌 것:
 * - 팀원이면 누구나 하는 일: 팀 채팅, 1:1 채팅, 회의 예약·참여
 * - 자원과의 관계로 정해지는 일: 채팅방 참여자의 방 초대, 회의 주최자의 자기 회의 취소
 *
 * @param deniedError 권한이 없을 때 돌려줄 에러
 */
enum class TeamPermission(val deniedError: ErrorCode) {
    /** 팀원 초대, 보낸 초대 취소 */
    INVITE_MEMBER(ErrorCode.CANNOT_INVITE),

    /** 그룹(1:N) 채팅방 만들기 */
    CREATE_GROUP_ROOM(ErrorCode.NOT_TEAM_OWNER),

    /** 다른 사람이 연 예약 회의 고치기·취소 */
    CANCEL_ANY_MEETING(ErrorCode.NOT_MEETING_HOST),

    /** 팀 채팅방에서 다른 사람이 보낸 메시지 지우기 (부적절한 메시지 정리) */
    DELETE_ANY_MESSAGE(ErrorCode.CANNOT_DELETE_MESSAGE),

    /** 팀원의 역할 바꾸기 (ADMIN 지정·해제) */
    MANAGE_ROLES(ErrorCode.NOT_TEAM_OWNER),

    /** 팀원 내보내기 (ADMIN 은 일반 팀원만 내보낼 수 있다 — TeamMember.canRemove) */
    REMOVE_MEMBER(ErrorCode.CANNOT_REMOVE_MEMBER),

    /** 팀 삭제 */
    DELETE_TEAM(ErrorCode.NOT_TEAM_OWNER),
}

/** 팀 안 역할과 그 역할의 권한. 역할을 추가하거나 권한을 조정할 때는 여기만 바꾼다 */
enum class TeamRole(val permissions: Set<TeamPermission>) {
    /** 팀을 만든 사람: 모든 권한 (권한이 새로 생기면 자동으로 포함) */
    OWNER(TeamPermission.entries.toSet()),

    /** 운영을 돕는 팀원: 초대, 회의 정리 */
    ADMIN(setOf(TeamPermission.INVITE_MEMBER, TeamPermission.CANCEL_ANY_MEETING, TeamPermission.REMOVE_MEMBER, TeamPermission.DELETE_ANY_MESSAGE)),

    /** 일반 팀원 */
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

    /** ADMIN 지정·해제. OWNER 는 팀을 만든 사람 한 명뿐이라 주거나 뺏을 수 없다 */
    fun changeRole(next: TeamRole) {
        if (role == TeamRole.OWNER || next == TeamRole.OWNER) throw BusinessException(ErrorCode.OWNER_ROLE_FIXED)
        role = next
    }

    /** 이 팀원이 [target] 을 내보낼 수 있는지: 권한이 있고, OWNER·자기 자신은 안 되며, ADMIN 은 일반 팀원만 */
    fun canRemove(target: TeamMember): Boolean =
        can(TeamPermission.REMOVE_MEMBER) && target.userId != userId && target.role != TeamRole.OWNER &&
            (role == TeamRole.OWNER || target.role == TeamRole.MEMBER)

    /** 활동 중인 팀원이고 역할에 [permission] 이 있는지 */
    fun can(permission: TeamPermission): Boolean = status == TeamMemberStatus.ACTIVE && role.can(permission)

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
