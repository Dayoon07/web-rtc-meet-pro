package com.webrtcmeetpro.application.admin

import com.webrtcmeetpro.application.admin.table.AdminTableService
import com.webrtcmeetpro.application.user.UserService
import com.webrtcmeetpro.config.properties.AdminProperties
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 이메일 인증을 기다리는 가입 한 명 */
data class PendingSignupView(
    val id: Long,
    val email: String,
    val nickname: String,
    val realName: String?,
    /** 이메일로 가입 (비밀번호 있음) */
    val hasPassword: Boolean,
    val createdAt: LocalDateTime,
    val lastLoginAt: LocalDateTime?,
)

/**
 * 관리자 사이트 "이메일 인증": 인증 메일을 못 받았거나(스팸·회사 메일 차단) 잘못된 메일로 가입한 사람을 관리자가 처리한다.
 * - 승인: 이메일 인증을 마친 것으로 바꾼다 → 바로 서비스를 쓸 수 있다
 * - 거절: 가입을 취소한다 (개인정보를 지우고 계정을 닫는다 → 같은 이메일로 다시 가입할 수 있다)
 * 처리한 내용은 데이터 변경 기록(admin_data_changes)에 남긴다.
 */
@Service
class AdminEmailVerificationService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val props: AdminProperties,
    private val users: UserService,
    private val tables: AdminTableService,
) {
    private val usersTable get() = props.tablePrefix + "users"

    /** 인증을 기다리는 계정 (최근 가입 순 200명). q = 이메일·닉네임·이름 일부 */
    fun pending(q: String?): List<PendingSignupView> {
        val params = MapSqlParameterSource()
        val search = q?.trim()?.takeIf { it.isNotEmpty() }?.let {
            params.addValue("q", "%" + it.uppercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
            " AND (UPPER(email) LIKE :q ESCAPE '\\' OR UPPER(nickname) LIKE :q ESCAPE '\\' OR UPPER(real_name) LIKE :q ESCAPE '\\')"
        }.orEmpty()
        return jdbc.query(
            """
            SELECT user_id, email, nickname, real_name, password_hash, created_dt, last_login_at FROM $usersTable
            WHERE email_verified_at IS NULL AND status = 'ACTIVE' AND deleted_yn = 'N'$search
            ORDER BY user_id DESC FETCH FIRST 200 ROWS ONLY
            """.trimIndent(),
            params,
        ) { rs, _ ->
            PendingSignupView(
                id = rs.getLong("user_id"),
                email = rs.getString("email"),
                nickname = rs.getString("nickname"),
                realName = rs.getString("real_name"),
                hasPassword = rs.getString("password_hash") != null,
                createdAt = rs.getTimestamp("created_dt").toLocalDateTime(),
                lastLoginAt = rs.getTimestamp("last_login_at")?.toLocalDateTime(),
            )
        }
    }

    /** 강제 승인: 인증 메일 없이 인증된 것으로 */
    @Transactional
    fun approve(userId: Long, adminId: Long) {
        val email = users.verifyEmailByAdmin(userId)
        tables.recordChange(usersTable, userId.toString(), "UPDATE", mapOf("email_verified_at" to mapOf("from" to null, "to" to "관리자 승인 ($email)")), adminId)
    }

    /** 거절: 가입 취소 (개인정보 지우고 계정 닫기 → 같은 이메일로 다시 가입 가능) */
    @Transactional
    fun reject(userId: Long, adminId: Long) {
        val email = users.rejectSignup(userId)
        tables.recordChange(usersTable, userId.toString(), "UPDATE", mapOf("status" to mapOf("from" to "ACTIVE", "to" to "DELETED (이메일 인증 거절 · $email)")), adminId)
    }
}
