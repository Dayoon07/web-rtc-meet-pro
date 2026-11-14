package com.webrtcmeetpro.presentation.meeting

import com.webrtcmeetpro.domain.meeting.dto.req.CaptionLangReqDto
import com.webrtcmeetpro.domain.meeting.dto.req.NotesLineReqDto
import com.webrtcmeetpro.application.meeting.LiveCaptionService
import com.webrtcmeetpro.common.web.userId
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 실시간 자막·번역 (Business). 켜져 있으면 각 참여자 브라우저가 받아쓴 문장을 lines 로 보내고,
 * 서버가 번역해서 /user/queue/meeting 으로 CAPTION 이벤트를 보낸다.
 */
@RestController
@RequestMapping("/api/meetings/{meetingId}/captions")
class LiveCaptionController(private val captions: LiveCaptionService) {

    @GetMapping("/state")
    fun state(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = captions.state(meetingId, jwt.userId)

    @PostMapping("/start")
    fun start(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long, @Valid @RequestBody(required = false) body: CaptionLangReqDto?) =
        captions.start(meetingId, jwt.userId, body?.lang)

    @PostMapping("/stop")
    fun stop(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = captions.stop(meetingId, jwt.userId)

    @PostMapping("/watch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun watch(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long, @Valid @RequestBody body: CaptionLangReqDto) =
        captions.watch(meetingId, jwt.userId, body.lang)

    @PostMapping("/lines")
    fun line(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long, @Valid @RequestBody body: NotesLineReqDto) =
        captions.addLine(meetingId, jwt.userId, body.text, body.lang)
}
