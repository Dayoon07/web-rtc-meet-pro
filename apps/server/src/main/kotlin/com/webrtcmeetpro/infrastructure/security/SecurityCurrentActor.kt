package com.webrtcmeetpro.infrastructure.security

import com.webrtcmeetpro.domain.common.CurrentActor
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/** JWT 로 인증된 요청이면 subject(= user_id), 아니면 null (로그인 전 요청, 스케줄러, 비동기 작업, 미디어 서버 콜백) */
@Component
class SecurityCurrentActor : CurrentActor {
    override fun userId(): Long? =
        (SecurityContextHolder.getContext().authentication?.principal as? Jwt)?.subject?.toLongOrNull()
}
