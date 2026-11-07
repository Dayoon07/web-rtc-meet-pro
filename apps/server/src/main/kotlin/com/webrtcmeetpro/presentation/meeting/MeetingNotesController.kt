package com.webrtcmeetpro.presentation.meeting

import com.webrtcmeetpro.application.meeting.MeetingNotesService
import com.webrtcmeetpro.common.web.userId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class NotesLineRequest(
    @field:NotBlank @field:Size(max = 2000) val text: String,
    @field:Size(max = 10) val lang: String? = null,
)

/**
 * AI 회의록. 회의 중에 켜고(start)·끄고(stop), 켜져 있는 동안 각 참여자 브라우저가 받아쓴 문장을 lines 로 보낸다.
 * 회의가 끝나면 서버가 요약해서 GET 으로 볼 수 있다.
 */
@RestController
@RequestMapping("/api/meetings/{meetingId}/notes")
class MeetingNotesController(private val notes: MeetingNotesService) {

    @GetMapping
    fun view(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = notes.view(meetingId, jwt.userId)

    @GetMapping("/state")
    fun state(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = notes.state(meetingId, jwt.userId)

    @GetMapping("/transcript")
    fun transcript(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = notes.transcript(meetingId, jwt.userId)

    @PostMapping("/start")
    fun start(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = notes.start(meetingId, jwt.userId)

    @PostMapping("/stop")
    fun stop(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = notes.stop(meetingId, jwt.userId)

    @PostMapping("/lines")
    fun line(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long, @Valid @RequestBody body: NotesLineRequest) =
        notes.addLine(meetingId, jwt.userId, body.text, body.lang)

    @PostMapping("/regenerate")
    fun regenerate(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = notes.regenerate(meetingId, jwt.userId)
}
