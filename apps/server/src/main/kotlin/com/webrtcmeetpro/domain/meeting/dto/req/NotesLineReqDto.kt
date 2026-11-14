package com.webrtcmeetpro.domain.meeting.dto.req

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class NotesLineReqDto(
    @field:NotBlank @field:Size(max = 2000) val text: String,
    @field:Size(max = 10) val lang: String? = null,
)
