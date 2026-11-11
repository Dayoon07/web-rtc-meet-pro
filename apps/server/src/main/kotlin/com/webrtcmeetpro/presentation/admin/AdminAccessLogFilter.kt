package com.webrtcmeetpro.presentation.admin

import com.webrtcmeetpro.common.web.ClientInfo
import com.webrtcmeetpro.domain.admin.AdminAccessLog
import com.webrtcmeetpro.domain.admin.AdminAccessLogRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 관리자 API(/api/admin 아래 전부)를 부를 때마다 접속기록을 남긴다 (개인정보의 안전성 확보조치 기준 제8조).
 * 보안 필터 뒤에서 돌아서 로그인한 관리자를 안다. 기록이 실패해도 요청은 막지 않는다.
 * 쿼리 문자열의 비밀값(token, password 등)은 가린다.
 */
@Component
class AdminAccessLogFilter(private val logs: AdminAccessLogRepository) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/api/admin/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        try {
            chain.doFilter(request, response)
        } finally {
            val adminId = (SecurityContextHolder.getContext().authentication?.principal as? Jwt)?.subject?.toLongOrNull()
            if (adminId != null) {
                runCatching {
                    val client = ClientInfo.from(request)
                    logs.save(
                        AdminAccessLog(
                            adminId = adminId,
                            httpMethod = request.method.take(10),
                            requestPath = request.requestURI.take(1000),
                            queryString = request.queryString?.let(::mask)?.take(1000),
                            statusCode = response.status,
                            ipAddress = client.ipAddress,
                            userAgent = client.userAgent,
                        ),
                    )
                }.onFailure { log.warn("관리자 접속기록 저장 실패: {}", it.toString()) }
            }
        }
    }

    companion object {
        private val SECRET = Regex("""(?i)((?:token|password|secret|key)[^=&]*)=[^&]*""")
        fun mask(query: String): String = SECRET.replace(query) { "${it.groupValues[1]}=***" }
    }
}
