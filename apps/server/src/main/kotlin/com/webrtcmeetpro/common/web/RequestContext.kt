package com.webrtcmeetpro.common.web

import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.jwt.Jwt

/** 접속 IP / User-Agent. limeet-app 이 콘솔에만 찍던 값을 DB 에 남기기 위해 사용 */
data class ClientInfo(val ipAddress: String?, val userAgent: String?) {
    companion object {
        fun from(request: HttpServletRequest): ClientInfo {
            val forwarded = request.getHeader("X-Forwarded-For")
            val ip = forwarded?.split(",")?.firstOrNull()?.trim()?.ifBlank { null } ?: request.remoteAddr
            return ClientInfo(ip?.take(45), request.getHeader("User-Agent")?.take(500))
        }
    }
}

/** JWT subject = user_id */
val Jwt.userId: Long
    get() = subject?.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
