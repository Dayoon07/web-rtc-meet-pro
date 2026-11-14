package com.webrtcmeetpro.domain.user.dto.req

/** 구글로만 가입한 계정은 비워 둔다 */
data class WithdrawReqDto(val password: String? = null)
