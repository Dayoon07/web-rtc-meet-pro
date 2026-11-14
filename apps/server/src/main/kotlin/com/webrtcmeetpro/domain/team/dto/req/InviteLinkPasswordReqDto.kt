package com.webrtcmeetpro.domain.team.dto.req

import jakarta.validation.constraints.Size

/** 초대 링크 만들기·들어가기. password 가 비어 있으면 비밀번호 없는 링크 */
data class InviteLinkPasswordReqDto(@field:Size(max = 64) val password: String? = null)
