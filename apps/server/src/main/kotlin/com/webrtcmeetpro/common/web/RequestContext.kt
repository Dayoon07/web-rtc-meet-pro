package com.webrtcmeetpro.common.web

import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.jwt.Jwt

data class ClientInfo(val ipAddress: String?, val userAgent: String?) {
    companion object {
        fun from(request: HttpServletRequest): ClientInfo {
            val forwarded = request.getHeader("X-Forwarded-For")
            val ip = forwarded?.split(",")?.firstOrNull()?.trim()?.ifBlank { null } ?: request.remoteAddr
            return ClientInfo(ip?.take(45), request.getHeader("User-Agent")?.take(500))
        }
    }
}

val Jwt.userId: Long
    get() = subject?.toLongOrNull() ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
