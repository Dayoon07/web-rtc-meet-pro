package com.webrtcmeetpro.team.presentation

import com.webrtcmeetpro.common.web.userId
import com.webrtcmeetpro.team.application.TeamService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
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

@RestController
@RequestMapping("/api")
class TeamController(private val teamService: TeamService) {

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
    @GetMapping("/invitations")
    fun myInvitations(@AuthenticationPrincipal jwt: Jwt) = teamService.myInvitations(jwt.userId)

    @PostMapping("/invitations/{invitationId}/accept")
    fun accept(@AuthenticationPrincipal jwt: Jwt, @PathVariable invitationId: Long) = teamService.accept(invitationId, jwt.userId)

    @PostMapping("/invitations/{invitationId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun decline(@AuthenticationPrincipal jwt: Jwt, @PathVariable invitationId: Long) = teamService.decline(invitationId, jwt.userId)
}
