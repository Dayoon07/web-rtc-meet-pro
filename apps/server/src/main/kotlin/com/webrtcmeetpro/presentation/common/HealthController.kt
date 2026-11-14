package com.webrtcmeetpro.presentation.common

import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** deploy/watchdog.sh 가 5분마다 보므로 DB 까지 확인해서 안 되면 503 을 준다 */
@RestController
class HealthController(private val jdbc: JdbcTemplate) {
    @GetMapping("/api/health")
    fun health(): ResponseEntity<Map<String, String>> {
        val db = runCatching { jdbc.queryForObject("SELECT 1 FROM dual", Int::class.java) }.isSuccess
        return if (db) ResponseEntity.ok(mapOf("status" to "ok", "db" to "ok"))
        else ResponseEntity.status(503).body(mapOf("status" to "down", "db" to "down"))
    }
}
