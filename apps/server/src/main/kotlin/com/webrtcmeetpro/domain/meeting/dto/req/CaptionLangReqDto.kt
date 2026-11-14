package com.webrtcmeetpro.domain.meeting.dto.req

import jakarta.validation.constraints.Size

/** lang: 내가 볼 자막 언어 (ko, en, ja, zh 또는 ko-KR 같은 브라우저 언어) */
data class CaptionLangReqDto(@field:Size(max = 10) val lang: String? = null)
