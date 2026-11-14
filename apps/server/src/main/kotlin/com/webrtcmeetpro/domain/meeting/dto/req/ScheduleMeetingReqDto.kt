package com.webrtcmeetpro.domain.meeting.dto.req

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class ScheduleMeetingReqDto(
    @field:NotBlank @field:Size(max = 100) val title: String,
    @field:Size(max = 1000) val description: String? = null,
    /** 생략하면 바로 시작. 오프셋이 없으면 서버 시간(Asia/Seoul)으로 본다 */
    val scheduledAt: LocalDateTime? = null,
    @field:Min(5) @field:Max(1440) val durationMinutes: Int = 60,
    @field:Size(max = 200) val inviteeIds: List<Long> = emptyList(),
)
