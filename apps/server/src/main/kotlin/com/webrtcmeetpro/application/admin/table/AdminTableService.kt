package com.webrtcmeetpro.application.admin.table

import com.webrtcmeetpro.domain.admin.dto.res.AdminColumnKind
import com.webrtcmeetpro.domain.admin.dto.res.AdminColumnRefResDto
import com.webrtcmeetpro.domain.admin.dto.res.AdminColumnResDto
import com.webrtcmeetpro.domain.admin.dto.res.AdminRowPageResDto
import com.webrtcmeetpro.domain.admin.dto.res.AdminTableMetaResDto
import com.webrtcmeetpro.domain.admin.dto.res.AdminTableSummaryResDto
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.properties.AdminProperties
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/** filters 의 값이 빈 문자열이면 IS NULL */
data class AdminRowQuery(
    val page: Int = 0,
    val size: Int = 50,
    val sort: String? = null,
    val desc: Boolean = true,
    val q: String? = null,
    val filters: Map<String, String> = emptyMap(),
    /** 대소문자 무시 부분 일치. 숫자·날짜도 글자로 바꿔 찾는다 */
    val contains: Map<String, String> = emptyMap(),
    /** null = 전부, false = 사용 중(N)만, true = 미사용(Y)만 */
    val deleted: Boolean? = null,
)

/**
 * Oracle 사전에서 테이블·컬럼·PK·FK·CHECK 를 읽어 어떤 테이블이든 같은 화면으로 다룬다.
 * SQL 에 넣는 테이블·컬럼 이름은 사전에서 읽은 것만 쓰고, 사용자 입력은 모두 바인드 변수로 넘긴다.
 */
@Service
class AdminTableService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val props: AdminProperties,
    private val json: JsonMapper,
) {
    private val zone = ZoneId.systemDefault()
    private val metaCache = ConcurrentHashMap<String, Pair<Instant, AdminTableMetaResDto>>()

    fun tables(): List<AdminTableSummaryResDto> =
        tableNames().map { name ->
            val key = policyKey(name)
            val info = AdminTablePolicy.infoOf(key)
            val count = jdbc.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ${q(name)}", java.lang.Long::class.java)?.toLong() ?: 0L
            AdminTableSummaryResDto(
                name = name,
                label = info?.label ?: tableComment(name) ?: name,
                group = info?.group ?: "기타",
                description = info?.description,
                rowCount = count,
                readOnly = key in AdminTablePolicy.readOnlyTables,
                insertable = key in AdminTablePolicy.insertableTables,
            )
        }.sortedWith(compareBy({ AdminTablePolicy.order(policyKey(it.name)) }, { it.name }))

    /** 사전 조회가 여러 번 필요해 30초 캐시한다. 새 컬럼은 길어야 30초 뒤에 보인다 */
    fun meta(table: String): AdminTableMetaResDto {
        val name = table.lowercase()
        metaCache[name]?.let { (at, meta) -> if (at.plusSeconds(30).isAfter(Instant.now())) return meta }
        if (name !in tableNames()) throw BusinessException(ErrorCode.ADMIN_TABLE_NOT_FOUND)
        return loadMeta(name).also { metaCache[name] = Instant.now() to it }
    }

    private fun tableNames(): List<String> =
        jdbc.jdbcTemplate.queryForList("SELECT LOWER(table_name) FROM user_tables WHERE table_name NOT LIKE 'BIN$%'", String::class.java)
            .filterNotNull().filter { it.startsWith(props.tablePrefix) }

    /** 테스트 테이블은 zz_ 같은 접두어가 붙어 있다 */
    private fun policyKey(name: String) = name.removePrefix(props.tablePrefix)

    private fun tableComment(name: String): String? = jdbc.queryForList(
        "SELECT comments FROM user_tab_comments WHERE table_name = :t",
        mapOf("t" to name.uppercase()),
        String::class.java,
    ).firstOrNull()?.takeIf { it.isNotBlank() }

    private fun loadMeta(name: String): AdminTableMetaResDto {
        val t = mapOf("t" to name.uppercase())
        val key = policyKey(name)
        val pk = jdbc.queryForList(
            """SELECT LOWER(cc.column_name) FROM user_constraints c
               JOIN user_cons_columns cc ON cc.constraint_name = c.constraint_name
               WHERE c.table_name = :t AND c.constraint_type = 'P'""",
            t, String::class.java,
        ).filterNotNull().singleOrNull()
        val fks = jdbc.query(
            """SELECT LOWER(a.column_name) col, LOWER(rc.table_name) rt, LOWER(rcc.column_name) rcol
               FROM user_constraints c
               JOIN user_cons_columns a ON a.constraint_name = c.constraint_name
               JOIN user_constraints rc ON rc.constraint_name = c.r_constraint_name
               JOIN user_cons_columns rcc ON rcc.constraint_name = rc.constraint_name AND rcc.position = a.position
               WHERE c.table_name = :t AND c.constraint_type = 'R'""",
            t,
        ) { rs, _ -> rs.getString("col") to AdminColumnRefResDto(rs.getString("rt"), rs.getString("rcol")) }.toMap()
        val referencedBy = jdbc.query(
            """SELECT LOWER(a.column_name) col, LOWER(c.table_name) tbl, LOWER(pcc.column_name) pcol
               FROM user_constraints c
               JOIN user_cons_columns a ON a.constraint_name = c.constraint_name
               JOIN user_constraints p ON p.constraint_name = c.r_constraint_name
               JOIN user_cons_columns pcc ON pcc.constraint_name = p.constraint_name AND pcc.position = a.position
               WHERE p.table_name = :t AND c.constraint_type = 'R'
               ORDER BY c.table_name""",
            t,
        ) { rs, _ -> rs.getString("pcol") to AdminColumnRefResDto(rs.getString("tbl"), rs.getString("col")) }
            .groupBy({ it.first }, { it.second })
        val options = jdbc.queryForList(
            "SELECT search_condition_vc FROM user_constraints WHERE table_name = :t AND constraint_type = 'C' AND search_condition_vc IS NOT NULL",
            t, String::class.java,
        ).filterNotNull().mapNotNull(::parseInCheck).toMap()

        val tableReadOnly = key in AdminTablePolicy.readOnlyTables
        val extraReadOnly = AdminTablePolicy.readOnlyColumns[key].orEmpty()
        val columns = jdbc.query(
            """SELECT LOWER(c.column_name) name, c.data_type, c.data_length, c.char_length, c.char_used,
                      c.data_precision, c.data_scale, c.nullable, c.identity_column, cc.comments
               FROM user_tab_columns c
               LEFT JOIN user_col_comments cc ON cc.table_name = c.table_name AND cc.column_name = c.column_name
               WHERE c.table_name = :t ORDER BY c.column_id""",
            t,
        ) { rs, _ ->
            val col = rs.getString("name")
            val type = rs.getString("data_type")
            val opts = options[col].orEmpty()
            val secret = AdminTablePolicy.isSecret(col)
            val kind = kindOf(type, opts)
            val charUsed = rs.getString("char_used")
            val maxLength = when (kind) {
                AdminColumnKind.TEXT, AdminColumnKind.YN, AdminColumnKind.ENUM ->
                    if (charUsed == "C") rs.getInt("char_length") else rs.getInt("data_length")
                else -> null
            }
            val isPk = col == pk
            val editable = !tableReadOnly && !isPk && !secret &&
                rs.getString("identity_column") != "YES" &&
                col !in AdminTablePolicy.auditColumns && col !in extraReadOnly &&
                kind != AdminColumnKind.BINARY && kind != AdminColumnKind.LONG_TEXT
            AdminColumnResDto(
                name = col,
                comment = rs.getString("comments")?.takeIf { it.isNotBlank() },
                kind = kind,
                dbType = dbTypeOf(type, rs.getInt("data_precision").takeIf { !rs.wasNull() }, rs.getInt("data_scale"), maxLength, charUsed),
                maxLength = maxLength,
                nullable = rs.getString("nullable") == "Y",
                primaryKey = isPk,
                editable = editable,
                secret = secret,
                options = opts,
                references = fks[col],
                referencedBy = referencedBy[col].orEmpty(),
            )
        }
        val info = AdminTablePolicy.infoOf(key)
        return AdminTableMetaResDto(
            name = name,
            label = info?.label ?: tableComment(name) ?: name,
            group = info?.group ?: "기타",
            description = info?.description,
            primaryKey = pk,
            // PK 가 하나가 아니면 행을 짚을 수 없다
            readOnly = tableReadOnly || pk == null,
            insertable = key in AdminTablePolicy.insertableTables && pk != null,
            softDelete = columns.any { it.name == "deleted_yn" },
            columns = columns,
        )
    }

    fun rows(table: String, query: AdminRowQuery): AdminRowPageResDto {
        val meta = meta(table)
        val size = query.size.coerceIn(1, 200)
        val page = query.page.coerceAtLeast(0)
        val params = MapSqlParameterSource()
        val where = whereOf(meta, query, params)
        val total = jdbc.queryForObject("SELECT COUNT(*) FROM ${q(meta.name)}$where", params, java.lang.Long::class.java)?.toLong() ?: 0L
        params.addValue("offset", page.toLong() * size).addValue("limit", size)
        val sql = "SELECT ${selectList(meta, full = false)} FROM ${q(meta.name)}$where ORDER BY ${orderOf(meta, query)} " +
            "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY"
        return AdminRowPageResDto(jdbc.query(sql, params) { rs, _ -> readRow(meta, rs) }, total, page, size)
    }

    fun row(table: String, id: String): Map<String, Any?> {
        val meta = meta(table)
        val pk = meta.primaryKey ?: throw BusinessException(ErrorCode.ADMIN_ROW_NOT_FOUND)
        val params = MapSqlParameterSource("id", convert(meta.column(pk), id))
        return jdbc.query("SELECT ${selectList(meta, full = true)} FROM ${q(meta.name)} WHERE ${q(pk)} = :id", params) { rs, _ -> readRow(meta, rs) }
            .firstOrNull() ?: throw BusinessException(ErrorCode.ADMIN_ROW_NOT_FOUND)
    }

    /** 엑셀에서 한글이 깨지지 않게 UTF-8 BOM 을 붙인다 */
    fun exportCsv(table: String, query: AdminRowQuery): String {
        val meta = meta(table)
        val params = MapSqlParameterSource()
        val where = whereOf(meta, query, params)
        val sql = "SELECT ${selectList(meta, full = false)} FROM ${q(meta.name)}$where ORDER BY ${orderOf(meta, query)} FETCH FIRST $EXPORT_LIMIT ROWS ONLY"
        val rows = jdbc.query(sql, params) { rs, _ -> readRow(meta, rs) }
        val cols = meta.columns.filterNot { it.secret }
        val sb = StringBuilder("﻿")
        sb.append(cols.joinToString(",") { csv(it.name) }).append("\r\n")
        rows.forEach { row -> sb.append(cols.joinToString(",") { csv(row[it.name]?.toString()) }).append("\r\n") }
        return sb.toString()
    }

    @Transactional
    fun update(table: String, id: String, values: Map<String, Any?>, adminId: Long): Map<String, Any?> {
        val meta = meta(table)
        if (meta.readOnly) throw BusinessException(ErrorCode.ADMIN_READ_ONLY)
        val pk = meta.primaryKey!!
        val before = row(table, id)
        val params = MapSqlParameterSource("id", convert(meta.column(pk), id))
        val changes = linkedMapOf<String, Map<String, Any?>>()
        val sets = mutableListOf<String>()
        values.forEach { (name, raw) ->
            val col = meta.columnOrNull(name) ?: throw invalid(name, "없는 컬럼입니다.")
            if (!col.editable) throw BusinessException(ErrorCode.ADMIN_READ_ONLY, mapOf("field" to name), "${name}: 고칠 수 없는 컬럼입니다.")
            guardSelfLockout(meta, col, id, raw, adminId)
            val value = convertInput(col, raw)
            if (sameValue(col, before[name], value)) return@forEach
            val p = "v${sets.size}"
            sets += "${q(name)} = :$p"
            params.addValue(p, value)
            changes[name] = mapOf("from" to before[name], "to" to displayOf(value))
        }
        if (sets.isEmpty()) return before
        if (meta.columnOrNull("edited_dt") != null) sets += "${q("edited_dt")} = SYSTIMESTAMP"
        if (meta.columnOrNull("edited_us") != null) {
            sets += "${q("edited_us")} = :adminId"
            params.addValue("adminId", adminId)
        }
        guardConstraint { jdbc.update("UPDATE ${q(meta.name)} SET ${sets.joinToString(", ")} WHERE ${q(pk)} = :id", params) }
        recordChange(meta.name, id, "UPDATE", changes, adminId)
        return row(table, id)
    }

    @Transactional
    fun insert(table: String, values: Map<String, Any?>, adminId: Long): Map<String, Any?> {
        val meta = meta(table)
        if (!meta.insertable) throw BusinessException(ErrorCode.ADMIN_READ_ONLY)
        val pk = meta.primaryKey!!
        val params = MapSqlParameterSource()
        val cols = mutableListOf<String>()
        val shown = linkedMapOf<String, Map<String, Any?>>()
        values.forEach { (name, raw) ->
            val col = meta.columnOrNull(name) ?: throw invalid(name, "없는 컬럼입니다.")
            if (!col.editable) throw BusinessException(ErrorCode.ADMIN_READ_ONLY, mapOf("field" to name), "${name}: 직접 넣을 수 없는 컬럼입니다.")
            val value = convertInput(col, raw) ?: return@forEach
            cols += name
            params.addValue("v${cols.size}", value)
            shown[name] = mapOf("from" to null, "to" to displayOf(value))
        }
        listOf("created_us", "edited_us").filter { meta.columnOrNull(it) != null }.forEach {
            cols += it
            params.addValue("v${cols.size}", adminId)
        }
        val keys = GeneratedKeyHolder()
        val sql = "INSERT INTO ${q(meta.name)} (${cols.joinToString(", ") { q(it) }}) VALUES (${cols.indices.joinToString(", ") { ":v${it + 1}" }})"
        guardConstraint { jdbc.update(sql, params, keys, arrayOf(pk.uppercase())) }
        val id = keys.keyList.firstOrNull()?.values?.firstOrNull()?.toString() ?: throw BusinessException(ErrorCode.ADMIN_ROW_NOT_FOUND)
        recordChange(meta.name, id, "INSERT", shown, adminId)
        return row(table, id)
    }

    /** 자기 관리자 권한을 빼면 관리자 사이트에 다시 들어올 수 없다 */
    private fun guardSelfLockout(meta: AdminTableMetaResDto, col: AdminColumnResDto, id: String, raw: Any?, adminId: Long) {
        val key = policyKey(meta.name)
        if (key == "users" && id == adminId.toString() &&
            ((col.name == "system_role" && raw != "ADMIN") || (col.name == "status" && raw != "ACTIVE") || (col.name == "deleted_yn" && raw != "N"))
        ) {
            throw BusinessException(ErrorCode.ADMIN_READ_ONLY, mapOf("field" to col.name), "내 계정의 관리자 권한·상태는 여기서 바꿀 수 없습니다. 다른 관리자에게 부탁해 주세요.")
        }
    }

    fun recordChange(table: String, id: String, action: String, changes: Map<String, Any?>, adminId: Long) {
        val log = props.tablePrefix + "admin_data_changes"
        if (log !in tableNames()) return
        jdbc.update(
            "INSERT INTO ${q(log)} (table_name, row_id, action, changes, admin_id, created_us, edited_us) VALUES (:t, :id, :a, :c, :admin, :admin, :admin)",
            MapSqlParameterSource()
                .addValue("t", table).addValue("id", id).addValue("a", action)
                .addValue("c", json.writeValueAsString(changes)).addValue("admin", adminId),
        )
    }

    private fun <T> guardConstraint(block: () -> T): T = try {
        block()
    } catch (e: DataIntegrityViolationException) {
        // ORA 메시지는 여러 줄이라 첫 줄만
        val detail = (e.mostSpecificCause.message ?: "").lineSequence().firstOrNull()?.take(300).orEmpty()
        throw BusinessException(ErrorCode.ADMIN_DATA_CONSTRAINT, mapOf("detail" to detail), "${ErrorCode.ADMIN_DATA_CONSTRAINT.message} ($detail)")
    }

    private fun whereOf(meta: AdminTableMetaResDto, query: AdminRowQuery, params: MapSqlParameterSource): String {
        val conds = mutableListOf<String>()
        query.filters.forEach { (name, value) ->
            val col = meta.columnOrNull(name)?.takeIf { !it.secret && it.kind != AdminColumnKind.BINARY && it.kind != AdminColumnKind.LONG_TEXT }
                ?: throw invalid(name, "이 컬럼으로는 거를 수 없습니다.")
            if (value.isEmpty()) {
                conds += "${q(name)} IS NULL"
            } else {
                val p = "f${conds.size}"
                conds += if (col.kind == AdminColumnKind.DATETIME) "TRUNC(${q(name)}) = TRUNC(:$p)" else "${q(name)} = :$p"
                params.addValue(p, convert(col, value))
            }
        }
        query.contains.forEach { (name, value) ->
            val col = meta.columnOrNull(name)?.takeIf { !it.secret && it.kind != AdminColumnKind.BINARY }
                ?: throw invalid(name, "이 컬럼으로는 찾을 수 없습니다.")
            val text = value.trim().takeIf { it.isNotEmpty() } ?: return@forEach
            val p = "c${conds.size}"
            val expr = when (col.kind) {
                AdminColumnKind.LONG_TEXT -> "UPPER(DBMS_LOB.SUBSTR(${q(name)}, 4000, 1))"
                AdminColumnKind.DATETIME -> "TO_CHAR(${q(name)}, 'YYYY-MM-DD HH24:MI:SS')"
                AdminColumnKind.NUMBER -> "TO_CHAR(${q(name)})"
                else -> "UPPER(${q(name)})"
            }
            conds += "$expr LIKE :$p ESCAPE '\\'"
            params.addValue(p, "%" + text.uppercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
        }
        if (query.deleted != null && meta.softDelete) conds += "${q("deleted_yn")} = '${if (query.deleted) "Y" else "N"}'"
        val text = query.q?.trim()?.takeIf { it.isNotEmpty() }
        if (text != null) {
            val ors = meta.columns
                .filter { !it.secret && it.kind in setOf(AdminColumnKind.TEXT, AdminColumnKind.ENUM, AdminColumnKind.LONG_TEXT) }
                .map { if (it.kind == AdminColumnKind.LONG_TEXT) "UPPER(DBMS_LOB.SUBSTR(${q(it.name)}, 4000, 1)) LIKE :q ESCAPE '\\'" else "UPPER(${q(it.name)}) LIKE :q ESCAPE '\\'" }
                .toMutableList()
            params.addValue("q", "%" + text.uppercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
            // 숫자로 찾으면 user_id 같은 숫자 컬럼도 같이 찾는다
            text.toBigDecimalOrNull()?.let { n ->
                meta.columns.filter { it.kind == AdminColumnKind.NUMBER && !it.secret }.forEach { ors += "${q(it.name)} = :qn" }
                params.addValue("qn", n)
            }
            if (ors.isNotEmpty()) conds += "(" + ors.joinToString(" OR ") + ")"
        }
        return if (conds.isEmpty()) "" else " WHERE " + conds.joinToString(" AND ")
    }

    private fun orderOf(meta: AdminTableMetaResDto, query: AdminRowQuery): String {
        val col = query.sort?.let { meta.columnOrNull(it) }?.takeIf { !it.secret && it.kind != AdminColumnKind.LONG_TEXT && it.kind != AdminColumnKind.BINARY }
            ?: meta.primaryKey?.let { meta.column(it) }
            ?: meta.columns.first()
        val dir = if (query.desc) "DESC NULLS LAST" else "ASC NULLS FIRST"
        // 같은 값끼리 순서가 매번 바뀌지 않게 PK 를 덧붙인다
        val tie = meta.primaryKey?.takeIf { it != col.name }?.let { ", ${q(it)} DESC" } ?: ""
        return "${q(col.name)} $dir$tie"
    }

    /** 목록이 무거워지지 않게 CLOB 은 앞부분만, 바이너리는 크기만 */
    private fun selectList(meta: AdminTableMetaResDto, full: Boolean): String = meta.columns.joinToString(", ") { c ->
        val n = q(c.name)
        when {
            c.secret -> "CASE WHEN $n IS NULL THEN 0 ELSE 1 END AS $n"
            c.kind == AdminColumnKind.BINARY -> "DBMS_LOB.GETLENGTH($n) AS $n"
            c.kind == AdminColumnKind.LONG_TEXT && !full -> "DBMS_LOB.SUBSTR($n, $LIST_TEXT_LIMIT, 1) AS $n"
            else -> n
        }
    }

    private fun readRow(meta: AdminTableMetaResDto, rs: ResultSet): Map<String, Any?> = meta.columns.associate { c ->
        val v: Any? = when {
            c.secret -> rs.getInt(c.name) == 1
            c.kind == AdminColumnKind.NUMBER || c.kind == AdminColumnKind.BINARY -> rs.getBigDecimal(c.name)?.let(::compactNumber)
            c.kind == AdminColumnKind.DATETIME -> rs.getTimestamp(c.name)?.toLocalDateTime()
            else -> rs.getString(c.name)
        }
        c.name to v
    }

    private fun convertInput(col: AdminColumnResDto, raw: Any?): Any? {
        val text = raw?.toString()
        if (text == null || (text.isEmpty() && col.kind != AdminColumnKind.TEXT)) {
            if (!col.nullable) throw invalid(col.name, "비워 둘 수 없습니다.")
            return null
        }
        return when (col.kind) {
            AdminColumnKind.TEXT -> text.also {
                val len = if (col.dbType.contains("CHAR)")) it.length else it.toByteArray().size
                if (col.maxLength != null && len > col.maxLength) throw invalid(col.name, "최대 ${col.maxLength}자(바이트)입니다.")
                if (it.isEmpty() && !col.nullable) throw invalid(col.name, "비워 둘 수 없습니다.")
            }.ifEmpty { null }
            AdminColumnKind.YN, AdminColumnKind.ENUM -> text.also {
                if (col.options.isNotEmpty() && it !in col.options) throw invalid(col.name, "${col.options.joinToString(", ")} 중 하나여야 합니다.")
            }
            else -> convert(col, text)
        }
    }

    private fun convert(col: AdminColumnResDto, text: String): Any = when (col.kind) {
        AdminColumnKind.NUMBER -> text.trim().toBigDecimalOrNull() ?: throw invalid(col.name, "숫자를 입력해 주세요.")
        AdminColumnKind.DATETIME -> Timestamp.valueOf(parseDateTime(text) ?: throw invalid(col.name, "날짜·시간 형식이 올바르지 않습니다. 예: 2026-11-07T09:30"))
        else -> text
    }

    private fun parseDateTime(text: String): LocalDateTime? {
        val s = text.trim()
        return runCatching { OffsetDateTime.parse(s).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s.replace(' ', 'T')) }.getOrNull()
            ?: runCatching { java.time.LocalDate.parse(s).atStartOfDay() }.getOrNull()
    }

    private fun sameValue(col: AdminColumnResDto, before: Any?, after: Any?): Boolean = when {
        before == null || after == null -> before == after
        col.kind == AdminColumnKind.NUMBER -> BigDecimal(before.toString()).compareTo(after as BigDecimal) == 0
        col.kind == AdminColumnKind.DATETIME -> (before as LocalDateTime) == (after as Timestamp).toLocalDateTime()
        else -> before.toString() == after.toString()
    }

    private fun displayOf(value: Any?): Any? = when (value) {
        is Timestamp -> value.toLocalDateTime().toString()
        is BigDecimal -> compactNumber(value)
        else -> value
    }

    private fun compactNumber(n: BigDecimal): Any =
        if (n.scale() <= 0 || n.stripTrailingZeros().scale() <= 0) n.toBigInteger().let { if (it.bitLength() < 63) it.toLong() else it } else n

    private fun invalid(field: String, reason: String) =
        BusinessException(ErrorCode.ADMIN_INVALID_VALUE, mapOf("field" to field), "${field}: $reason")

    private fun kindOf(type: String, options: List<String>): AdminColumnKind = when {
        options.toSet() == setOf("Y", "N") -> AdminColumnKind.YN
        options.isNotEmpty() -> AdminColumnKind.ENUM
        type == "NUMBER" || type == "FLOAT" || type.startsWith("BINARY_") -> AdminColumnKind.NUMBER
        type == "DATE" || type.startsWith("TIMESTAMP") -> AdminColumnKind.DATETIME
        type == "CLOB" || type == "NCLOB" || type == "LONG" -> AdminColumnKind.LONG_TEXT
        type == "BLOB" || type == "RAW" || type == "LONG RAW" -> AdminColumnKind.BINARY
        else -> AdminColumnKind.TEXT
    }

    private fun dbTypeOf(type: String, precision: Int?, scale: Int, length: Int?, charUsed: String?): String = when {
        type == "NUMBER" && precision != null -> if (scale > 0) "NUMBER($precision,$scale)" else "NUMBER($precision)"
        length != null && (type.contains("CHAR")) -> "$type($length${if (charUsed == "C") " CHAR" else ""})"
        else -> type
    }

    /** CHECK (status IN ('A', 'B')) → status to [A, B] */
    private fun parseInCheck(condition: String): Pair<String, List<String>>? {
        val m = IN_CHECK.matchEntire(condition.trim()) ?: return null
        val values = QUOTED.findAll(m.groupValues[2]).map { it.groupValues[1] }.toList()
        return if (values.isEmpty()) null else m.groupValues[1].lowercase() to values
    }

    private fun AdminTableMetaResDto.columnOrNull(name: String) = columns.firstOrNull { it.name == name.lowercase() }
    private fun AdminTableMetaResDto.column(name: String) = columnOrNull(name) ?: throw invalid(name, "없는 컬럼입니다.")

    /** Oracle 은 따옴표 없는 이름을 대문자로 바꾸므로 감싸서 정확히 가리킨다 */
    private fun q(identifier: String): String {
        require(IDENTIFIER.matches(identifier)) { "잘못된 이름: $identifier" }
        return "\"" + identifier.uppercase() + "\""
    }

    private fun csv(value: String?): String {
        val v = value ?: return ""
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    companion object {
        const val EXPORT_LIMIT = 10_000
        const val LIST_TEXT_LIMIT = 300
        private val IDENTIFIER = Regex("[A-Za-z][A-Za-z0-9_$#]{0,127}")
        private val IN_CHECK = Regex("""(?is)"?([A-Za-z0-9_$#]+)"?\s+IN\s*\((.*)\)""")
        private val QUOTED = Regex("'((?:[^']|'')*)'")
    }
}
