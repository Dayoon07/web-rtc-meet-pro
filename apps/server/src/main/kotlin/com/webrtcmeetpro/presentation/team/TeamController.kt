package com.webrtcmeetpro.presentation.team

import com.webrtcmeetpro.domain.team.dto.req.ChangeRoleReqDto
import com.webrtcmeetpro.domain.team.dto.req.CreateTeamReqDto
import com.webrtcmeetpro.domain.team.dto.req.InviteLinkPasswordReqDto
import com.webrtcmeetpro.domain.team.dto.req.InviteReqDto
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

@RestController
@RequestMapping("/api")
class TeamController(private val teamService: TeamService, private val inviteLinks: TeamInviteLinkService) {

    @GetMapping("/teams/{teamId}/invite-links")
    fun inviteLinks(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = inviteLinks.list(teamId, jwt.userId)

    @PostMapping("/teams/{teamId}/invite-links")
    @ResponseStatus(HttpStatus.CREATED)
    fun createInviteLink(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody req: InviteLinkPasswordReqDto) =
        inviteLinks.create(teamId, jwt.userId, req.password)

    @DeleteMapping("/teams/{teamId}/invite-links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteInviteLink(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @PathVariable linkId: Long) =
        inviteLinks.delete(teamId, linkId, jwt.userId)

    @GetMapping("/invite-links/{token}")
    fun inviteLinkPreview(@AuthenticationPrincipal jwt: Jwt, @PathVariable token: String) = inviteLinks.preview(token, jwt.userId)

    @PostMapping("/invite-links/{token}/join")
    fun joinByInviteLink(@AuthenticationPrincipal jwt: Jwt, @PathVariable token: String, @Valid @RequestBody req: InviteLinkPasswordReqDto) =
        inviteLinks.join(token, jwt.userId, req.password)

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody req: CreateTeamReqDto) =
        teamService.create(jwt.userId, req.name, req.description)

    @GetMapping("/teams")
    fun myTeams(@AuthenticationPrincipal jwt: Jwt) = teamService.myTeams(jwt.userId)

    @GetMapping("/teams/{teamId}")
    fun detail(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = teamService.detail(teamId, jwt.userId)

    @PostMapping("/teams/{teamId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    fun invite(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody req: InviteReqDto) =
        teamService.invite(teamId, jwt.userId, req.email)

    @GetMapping("/teams/{teamId}/invitations")
    fun teamInvitations(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) =
        teamService.teamInvitations(teamId, jwt.userId)

    @DeleteMapping("/teams/{teamId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @PathVariable invitationId: Long) =
        teamService.cancel(teamId, invitationId, jwt.userId)

    @PatchMapping("/teams/{teamId}/members/{userId}/role")
    fun changeRole(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable teamId: Long,
        @PathVariable userId: Long,
        @Valid @RequestBody req: ChangeRoleReqDto,
    ) = teamService.changeRole(teamId, jwt.userId, userId, req.role!!)

    @PostMapping("/teams/{teamId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = teamService.leave(teamId, jwt.userId)

    @DeleteMapping("/teams/{teamId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @PathVariable userId: Long) =
        teamService.removeMember(teamId, jwt.userId, userId)

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
