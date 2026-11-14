package com.webrtcmeetpro.domain.chat.dto.req

import jakarta.validation.constraints.Size

data class InviteMembersReqDto(@field:Size(min = 1, max = 200) val userIds: List<Long>)
