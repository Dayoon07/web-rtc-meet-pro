package com.webrtcmeetpro.domain.meeting.dto.req

import jakarta.validation.constraints.Size

/** ko-KR 같은 브라우저 언어도 받는다 */
data class CaptionLangReqDto(@field:Size(max = 10) val lang: String? = null)
