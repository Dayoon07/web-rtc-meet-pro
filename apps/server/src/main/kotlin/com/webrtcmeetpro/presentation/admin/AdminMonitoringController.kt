package com.webrtcmeetpro.presentation.admin

import com.webrtcmeetpro.domain.monitoring.dto.res.HealthReportResDto
import com.webrtcmeetpro.application.admin.SystemAdminAccess
import com.webrtcmeetpro.application.monitoring.HealthReportService
import com.webrtcmeetpro.common.web.userId
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 서버 상태: 지금 보기 · 지금 메일로 보내기 (12시간마다 자동으로도 간다) */
@RestController
@RequestMapping("/api/admin/monitoring")
class AdminMonitoringController(private val admins: SystemAdminAccess, private val reports: HealthReportService) {

    @GetMapping("/health")
    fun health(@AuthenticationPrincipal jwt: Jwt): HealthReportResDto {
        admins.require(jwt.userId)
        return reports.collect()
    }

    @PostMapping("/report")
    fun sendNow(@AuthenticationPrincipal jwt: Jwt): HealthReportResDto {
        admins.require(jwt.userId)
        return reports.sendReport()
    }
}
