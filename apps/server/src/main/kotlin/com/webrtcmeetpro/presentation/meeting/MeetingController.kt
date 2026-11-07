package com.webrtcmeetpro.presentation.meeting

import jakarta.validation.constraints.NotNull
import org.springframework.web.bind.annotation.PutMapping
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.common.web.userId
import com.webrtcmeetpro.application.meeting.MediaEventCommand
import com.webrtcmeetpro.application.meeting.MeetingService
import com.webrtcmeetpro.application.meeting.ScheduleMeetingCommand
import com.webrtcmeetpro.config.properties.MediaProperties
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.time.LocalDateTime

data class ScheduleMeetingRequest(
    @field:NotBlank @field:Size(max = 100) val title: String,
    @field:Size(max = 1000) val description: String? = null,
    /** 생략하면 바로 시작. 서버 로컬 시간(Asia/Seoul) 기준 ISO-8601 */
    val scheduledAt: LocalDateTime? = null,
    @field:Min(5) @field:Max(1440) val durationMinutes: Int = 60,
    @field:Size(max = 200) val inviteeIds: List<Long> = emptyList(),
)

/** 예약 회의 고치기. inviteeIds 를 비우면 팀원 전체 */
data class UpdateMeetingRequest(
    @field:NotBlank @field:Size(max = 100) val title: String,
    @field:Size(max = 1000) val description: String? = null,
    @field:NotNull val scheduledAt: LocalDateTime?,
    @field:Min(5) @field:Max(1440) val durationMinutes: Int = 60,
    @field:Size(max = 200) val inviteeIds: List<Long> = emptyList(),
)

@RestController
@RequestMapping("/api")
class MeetingController(private val meetings: MeetingService) {

    @PostMapping("/teams/{teamId}/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    fun schedule(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody req: ScheduleMeetingRequest) =
        meetings.schedule(
            teamId,
            jwt.userId,
            ScheduleMeetingCommand(req.title, req.description, req.scheduledAt, req.durationMinutes, req.inviteeIds),
        )

    /** 채널 상시 음성방 들어가기: 진행 중인 음성방, 없으면 새로 연다. 돌려준 회의 id 로 /meet/{id} 입장 */
    @PostMapping("/chat/rooms/{roomId}/voice")
    fun joinVoice(@AuthenticationPrincipal jwt: Jwt, @PathVariable roomId: Long) = meetings.joinVoice(roomId, jwt.userId)

    @GetMapping("/meetings")
    fun mine(@AuthenticationPrincipal jwt: Jwt, @RequestParam(defaultValue = "upcoming") scope: String) =
        meetings.myMeetings(jwt.userId, past = scope == "past")

    @GetMapping("/meetings/{meetingId}")
    fun detail(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = meetings.detail(meetingId, jwt.userId)

    /** 예약 회의 고치기 (주최자, 또는 팀 OWNER·ADMIN). 시작 전인 회의만 */
    @PutMapping("/meetings/{meetingId}")
    fun update(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long, @Valid @RequestBody req: UpdateMeetingRequest) =
        meetings.update(
            meetingId,
            jwt.userId,
            ScheduleMeetingCommand(req.title, req.description, req.scheduledAt, req.durationMinutes, req.inviteeIds),
        )

    @PostMapping("/meetings/{meetingId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = meetings.cancel(meetingId, jwt.userId)

    @PostMapping("/meetings/{meetingId}/join")
    fun join(@AuthenticationPrincipal jwt: Jwt, @PathVariable meetingId: Long) = meetings.join(meetingId, jwt.userId)
}

/**
 * mediasoup 사이드카 → 서버 콜백. JWT 가 아니라 공유 비밀 헤더로 인증한다.
 * 운영에서는 이 경로를 리버스 프록시에서 외부에 노출하지 말 것.
 */
@RestController
@RequestMapping("/internal/media")
class InternalMediaController(
    private val meetings: MeetingService,
    private val props: MediaProperties,
) {
    @PostMapping("/events")
    fun event(@RequestHeader("X-Internal-Secret", required = false) secret: String?, @RequestBody cmd: MediaEventCommand): Map<String, Any?> {
        val ok = secret != null && MessageDigest.isEqual(secret.toByteArray(), props.internalSecret.toByteArray())
        if (!ok) throw BusinessException(ErrorCode.FORBIDDEN)
        return meetings.recordMediaEvent(cmd)
    }
}
