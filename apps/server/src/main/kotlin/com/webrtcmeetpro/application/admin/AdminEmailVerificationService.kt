package com.webrtcmeetpro.application.admin

import com.webrtcmeetpro.application.admin.table.AdminTableService
import com.webrtcmeetpro.application.user.UserService
import com.webrtcmeetpro.config.properties.AdminProperties
import com.webrtcmeetpro.domain.admin.AdminQueryRepository
import com.webrtcmeetpro.domain.admin.dto.res.PendingSignupResDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 스팸함·회사 메일 차단으로 인증 메일을 못 받은 사람은 관리자가 대신 승인하거나 가입을 취소한다 */
@Service
class AdminEmailVerificationService(
    private val queries: AdminQueryRepository,
    private val props: AdminProperties,
    private val users: UserService,
    private val tables: AdminTableService,
) {
    private val usersTable get() = props.tablePrefix + "users"

    fun pending(q: String?): List<PendingSignupResDto> =
        queries.findPendingSignups(props.tablePrefix, q?.trim()?.takeIf { it.isNotEmpty() }?.let(::likeUpper))

    @Transactional
    fun approve(userId: Long, adminId: Long) {
        val email = users.verifyEmailByAdmin(userId)
        tables.recordChange(usersTable, userId.toString(), "UPDATE", mapOf("email_verified_at" to mapOf("from" to null, "to" to "관리자 승인 ($email)")), adminId)
    }

    /** 개인정보를 지우고 계정을 닫아 같은 이메일로 다시 가입할 수 있게 한다 */
    @Transactional
    fun reject(userId: Long, adminId: Long) {
        val email = users.rejectSignup(userId)
        tables.recordChange(usersTable, userId.toString(), "UPDATE", mapOf("status" to mapOf("from" to "ACTIVE", "to" to "DELETED (이메일 인증 거절 · $email)")), adminId)
    }
}
