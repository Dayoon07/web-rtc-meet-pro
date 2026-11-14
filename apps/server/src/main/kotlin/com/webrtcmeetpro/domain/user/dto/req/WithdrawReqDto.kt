package com.webrtcmeetpro.domain.user.dto.req

/** 회원 탈퇴 확인용 비밀번호 (구글로만 가입한 계정은 비워 둔다) */
data class WithdrawReqDto(val password: String? = null)
