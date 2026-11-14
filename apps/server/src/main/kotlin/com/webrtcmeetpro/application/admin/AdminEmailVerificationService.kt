package com.webrtcmeetpro.application.admin

import com.webrtcmeetpro.application.admin.table.AdminTableService
import com.webrtcmeetpro.application.user.UserService
import com.webrtcmeetpro.config.properties.AdminProperties
import com.webrtcmeetpro.domain.admin.AdminQueryRepository
import com.webrtcmeetpro.domain.admin.dto.res.PendingSignupResDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 사이트 "이메일 인증": 인증 메일을 못 받았거나(스팸·회사 메일 차단) 잘못된 메일로 가입한 사람을 관리자가 처리한다.
 * - 승인: 이메일 인증을 마친 것으로 바꾼다 → 바로 서비스를 쓸 수 있다
 * - 거절: 가입을 취소한다 (개인정보를 지우고 계정을 닫는다 → 같은 이메일로 다시 가입할 수 있다)
 * 처리한 내용은 데이터 변경 기록(admin_data_changes)에 남긴다.
 */
@Service
class AdminEmailVerificationService(
    private val queries: AdminQueryRepository,
    private val props: AdminProperties,
    private val users: UserService,
    private val tables: AdminTableService,
) {
    private val usersTable get() = props.tablePrefix + "users"

    /** 인증을 기다리는 계정 (최근 가입 순 200명). q = 이메일·닉네임·이름 일부 */
    fun pending(q: String?): List<PendingSignupResDto> =
        queries.findPendingSignups(props.tablePrefix, q?.trim()?.takeIf { it.isNotEmpty() }?.let(::likeUpper))

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
