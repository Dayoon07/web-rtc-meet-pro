package com.webrtcmeetpro.presentation.admin

import com.webrtcmeetpro.domain.admin.dto.req.AdminRowValuesReqDto
import com.webrtcmeetpro.application.admin.SystemAdminAccess
import com.webrtcmeetpro.application.admin.table.AdminRowQuery
import com.webrtcmeetpro.application.admin.table.AdminTableService
import com.webrtcmeetpro.common.web.userId
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/**
 * 관리자 사이트 "데이터 관리" API. 어떤 테이블이든 같은 주소 모양으로 다룬다.
 *   목록 필터: ?filter=team_id:5&filter=status:ACTIVE (같음, 값이 비면 IS NULL), ?contains=email:gmail (포함), ?q=검색어, ?deleted=active|deleted
 */
@RestController
@RequestMapping("/api/admin/tables")
class AdminTableController(
    private val tables: AdminTableService,
    private val admins: SystemAdminAccess,
) {
    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt) = admin(jwt) { tables.tables() }

    @GetMapping("/{table}")
    fun meta(@AuthenticationPrincipal jwt: Jwt, @PathVariable table: String) = admin(jwt) { tables.meta(table) }

    @GetMapping("/{table}/rows")
    fun rows(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable table: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "true") desc: Boolean,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) filter: List<String>?,
        @RequestParam(required = false) contains: List<String>?,
        @RequestParam(required = false) deleted: String?,
    ) = admin(jwt) { tables.rows(table, query(page, size, sort, desc, q, filter, deleted, contains)) }

    @GetMapping("/{table}/rows/{id}")
    fun row(@AuthenticationPrincipal jwt: Jwt, @PathVariable table: String, @PathVariable id: String) =
        admin(jwt) { tables.row(table, id) }

    @PatchMapping("/{table}/rows/{id}")
    fun update(@AuthenticationPrincipal jwt: Jwt, @PathVariable table: String, @PathVariable id: String, @RequestBody body: AdminRowValuesReqDto) =
        admin(jwt) { tables.update(table, id, body.values, jwt.userId) }

    @PostMapping("/{table}/rows")
    fun insert(@AuthenticationPrincipal jwt: Jwt, @PathVariable table: String, @RequestBody body: AdminRowValuesReqDto) =
        admin(jwt) { tables.insert(table, body.values, jwt.userId) }

    /** 지금 조건 그대로 CSV 로 (최대 1만 행) */
    @GetMapping("/{table}/export")
    fun export(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable table: String,
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "true") desc: Boolean,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) filter: List<String>?,
        @RequestParam(required = false) contains: List<String>?,
        @RequestParam(required = false) deleted: String?,
    ): ResponseEntity<ByteArray> = admin(jwt) {
        val csv = tables.exportCsv(table, query(0, 0, sort, desc, q, filter, deleted, contains))
        val name = "${table.lowercase()}-${LocalDate.now()}.csv"
        ResponseEntity.ok()
            .contentType(MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
            .body(csv.toByteArray(StandardCharsets.UTF_8))
    }

    private fun query(page: Int, size: Int, sort: String?, desc: Boolean, q: String?, filter: List<String>?, deleted: String?, contains: List<String>?) =
        AdminRowQuery(
            page = page,
            size = size,
            sort = sort?.ifBlank { null },
            desc = desc,
            q = q,
            filters = filter.orEmpty().filter { ':' in it }.associate { it.substringBefore(':') to it.substringAfter(':') },
            contains = contains.orEmpty().filter { ':' in it }.associate { it.substringBefore(':') to it.substringAfter(':') },
            deleted = when (deleted) {
                "active" -> false
                "deleted" -> true
                else -> null
            },
        )

    /** SecurityConfig 가 이미 막지만, 서비스 앞에서 한 번 더 확인한다 */
    private inline fun <T> admin(jwt: Jwt, block: () -> T): T {
        admins.require(jwt.userId)
        return block()
    }
}
