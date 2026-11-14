package com.webrtcmeetpro.presentation.common

import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 살아 있는지 (로그인 없이). DB 까지 확인해서 DB 가 안 되면 503 — deploy/watchdog.sh 가 5분마다 본다.
 * 자세한 상태(메모리·디스크 등)는 관리자 전용 /api/admin/monitoring/health
 */
@RestController
class HealthController(private val jdbc: JdbcTemplate) {
    @GetMapping("/api/health")
    fun health(): ResponseEntity<Map<String, String>> {
        val db = runCatching { jdbc.queryForObject("SELECT 1 FROM dual", Int::class.java) }.isSuccess
        return if (db) ResponseEntity.ok(mapOf("status" to "ok", "db" to "ok"))
        else ResponseEntity.status(503).body(mapOf("status" to "down", "db" to "down"))
    }
}
