package com.webrtcmeetpro.presentation.admin

import com.webrtcmeetpro.application.admin.AdminErrorLogService
import com.webrtcmeetpro.common.web.userId
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 API (/api/admin 아래 전부). 시스템 관리자만 — SecurityConfig 에서 한 번, 서비스에서 한 번 더 확인한다.
 * 관리자 사이트(별도 서브도메인)가 이 API 를 쓴다.
 */
@RestController
@RequestMapping("/api/admin")
class AdminController(private val errorLogs: AdminErrorLogService) {

    /** 최근 에러 100건. unresolved=true 면 해결 표시가 안 된 것만 */
    @GetMapping("/error-logs")
    fun errorLogs(@AuthenticationPrincipal jwt: Jwt, @RequestParam(defaultValue = "false") unresolved: Boolean) =
        errorLogs.recent(jwt.userId, unresolved)

    /** 사용자가 알려 준 에러 코드(ERR-...)로 찾기. 스택 포함 */
    @GetMapping("/error-logs/{errorCode}")
    fun errorLog(@AuthenticationPrincipal jwt: Jwt, @PathVariable errorCode: String) = errorLogs.find(jwt.userId, errorCode)

    @PostMapping("/error-logs/{errorCode}/resolve")
    fun resolve(@AuthenticationPrincipal jwt: Jwt, @PathVariable errorCode: String) = errorLogs.resolve(jwt.userId, errorCode)
}
