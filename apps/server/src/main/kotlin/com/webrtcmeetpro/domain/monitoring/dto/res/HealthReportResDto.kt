package com.webrtcmeetpro.domain.monitoring.dto.res

import java.time.LocalDateTime

/** warnings = 지금은 정상이지만 곧 문제가 될 것 */
data class HealthReportResDto(val at: LocalDateTime, val checks: List<HealthCheckResDto>, val warnings: List<String>) {
    val healthy: Boolean get() = checks.all { it.ok } && warnings.isEmpty()
}
