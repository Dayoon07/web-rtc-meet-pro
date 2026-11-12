package com.webrtcmeetpro.presentation.team

import com.webrtcmeetpro.common.web.userId
import com.webrtcmeetpro.application.team.TeamInviteLinkService
import com.webrtcmeetpro.application.team.TeamService
import com.webrtcmeetpro.domain.team.TeamRole
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class CreateTeamRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:Size(max = 500) val description: String? = null,
)

data class InviteRequest(@field:NotBlank @field:Email @field:Size(max = 255) val email: String)

/** 초대 링크 만들기·들어가기. password 가 비어 있으면 비밀번호 없는 링크 */
data class InviteLinkPasswordRequest(@field:Size(max = 64) val password: String? = null)

/** ADMIN 또는 MEMBER (OWNER 는 줄 수 없다) */
data class ChangeRoleRequest(@field:NotNull val role: TeamRole?)

@RestController
@RequestMapping("/api")
class TeamController(private val teamService: TeamService, private val inviteLinks: TeamInviteLinkService) {

    // ------------------------------------------------------------- 초대 링크 (팀 소유자·관리자가 만들고 지운다)

    @GetMapping("/teams/{teamId}/invite-links")
    fun inviteLinks(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = inviteLinks.list(teamId, jwt.userId)

    @PostMapping("/teams/{teamId}/invite-links")
    @ResponseStatus(HttpStatus.CREATED)
    fun createInviteLink(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody req: InviteLinkPasswordRequest) =
        inviteLinks.create(teamId, jwt.userId, req.password)

    @DeleteMapping("/teams/{teamId}/invite-links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteInviteLink(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @PathVariable linkId: Long) =
        inviteLinks.delete(teamId, linkId, jwt.userId)

    /** 링크를 연 사람: 어떤 팀인지 · 비밀번호가 필요한지 */
    @GetMapping("/invite-links/{token}")
    fun inviteLinkPreview(@AuthenticationPrincipal jwt: Jwt, @PathVariable token: String) = inviteLinks.preview(token, jwt.userId)

    /** 링크로 팀에 들어가기 (수락) */
    @PostMapping("/invite-links/{token}/join")
    fun joinByInviteLink(@AuthenticationPrincipal jwt: Jwt, @PathVariable token: String, @Valid @RequestBody req: InviteLinkPasswordRequest) =
        inviteLinks.join(token, jwt.userId, req.password)

    // -------------------------------------------------------------

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody req: CreateTeamRequest) =
        teamService.create(jwt.userId, req.name, req.description)

    @GetMapping("/teams")
    fun myTeams(@AuthenticationPrincipal jwt: Jwt) = teamService.myTeams(jwt.userId)

    @GetMapping("/teams/{teamId}")
    fun detail(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = teamService.detail(teamId, jwt.userId)

    @PostMapping("/teams/{teamId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    fun invite(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody req: InviteRequest) =
        teamService.invite(teamId, jwt.userId, req.email)

    @GetMapping("/teams/{teamId}/invitations")
    fun teamInvitations(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) =
        teamService.teamInvitations(teamId, jwt.userId)

    @DeleteMapping("/teams/{teamId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @PathVariable invitationId: Long) =
        teamService.cancel(teamId, invitationId, jwt.userId)

    /** 내가 받은 초대 */
    /** 팀원 역할 바꾸기 (ADMIN 지정·해제). 팀 OWNER 만 */
    @PatchMapping("/teams/{teamId}/members/{userId}/role")
    fun changeRole(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable teamId: Long,
        @PathVariable userId: Long,
        @Valid @RequestBody req: ChangeRoleRequest,
    ) = teamService.changeRole(teamId, jwt.userId, userId, req.role!!)

    /** 팀 나가기 (OWNER 는 못 나간다) */
    @PostMapping("/teams/{teamId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = teamService.leave(teamId, jwt.userId)

    /** 팀원 내보내기 (OWNER: 누구나, ADMIN: 일반 팀원만) */
    @DeleteMapping("/teams/{teamId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @PathVariable userId: Long) =
        teamService.removeMember(teamId, jwt.userId, userId)

    /** 팀 삭제 (OWNER) */
    @DeleteMapping("/teams/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = teamService.delete(teamId, jwt.userId)

    @GetMapping("/invitations")
    fun myInvitations(@AuthenticationPrincipal jwt: Jwt) = teamService.myInvitations(jwt.userId)

    @PostMapping("/invitations/{invitationId}/accept")
    fun accept(@AuthenticationPrincipal jwt: Jwt, @PathVariable invitationId: Long) = teamService.accept(invitationId, jwt.userId)

    @PostMapping("/invitations/{invitationId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun decline(@AuthenticationPrincipal jwt: Jwt, @PathVariable invitationId: Long) = teamService.decline(invitationId, jwt.userId)
}
