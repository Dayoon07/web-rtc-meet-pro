package com.webrtcmeetpro.domain.meeting.dto.req

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/** 예약 회의 고치기. inviteeIds 를 비우면 팀원 전체 */
data class UpdateMeetingReqDto(
    @field:NotBlank @field:Size(max = 100) val title: String,
    @field:Size(max = 1000) val description: String? = null,
    @field:NotNull val scheduledAt: LocalDateTime?,
    @field:Min(5) @field:Max(1440) val durationMinutes: Int = 60,
    @field:Size(max = 200) val inviteeIds: List<Long> = emptyList(),
)
