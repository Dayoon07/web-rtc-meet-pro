package com.webrtcmeetpro.application.admin

import com.webrtcmeetpro.domain.admin.dto.res.AdminErrorLogResDto
import com.webrtcmeetpro.domain.admin.dto.res.AdminMeResDto
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.domain.admin.AdminErrorLog
import com.webrtcmeetpro.domain.admin.AdminErrorLogRepository
import com.webrtcmeetpro.domain.admin.ErrorCategory
import com.webrtcmeetpro.domain.admin.ErrorSource
import com.webrtcmeetpro.domain.user.UserRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 시스템 관리자(users.system_role = ADMIN) 확인. 팀 역할과는 별개다.
 * 토큰에 역할을 넣지 않고 매번 DB 에서 읽는다 → 관리자 권한을 회수하면 바로 막힌다.
 */
@Component
class SystemAdminAccess(private val userRepository: UserRepository) {

    fun isSystemAdmin(userId: Long): Boolean = userRepository.findUserById(userId)?.isSystemAdmin == true

    fun require(userId: Long) {
        if (!isSystemAdmin(userId)) throw BusinessException(ErrorCode.FORBIDDEN)
    }

    /** 관리자 사이트가 로그인 직후 "관리자 계정인지" 확인할 때 */
    fun me(userId: Long): AdminMeResDto {
        val user = userRepository.findUserById(userId)?.takeIf { it.isSystemAdmin } ?: throw BusinessException(ErrorCode.FORBIDDEN)
        return AdminMeResDto(user.id!!, user.email, user.nickname)
    }
}

/** 사용자가 알려 준 에러 코드로 원인을 찾고, 해결 표시를 한다 (관리자 API) */
@Service
@Transactional(readOnly = true)
class AdminErrorLogService(
    private val repository: AdminErrorLogRepository,
    private val admins: SystemAdminAccess,
) {

    /** 최근 100건. unresolvedOnly 면 아직 해결 표시가 안 된 것만 */
    fun recent(adminId: Long, unresolvedOnly: Boolean): List<AdminErrorLogResDto> {
        admins.require(adminId)
        val logs = if (unresolvedOnly) repository.findTop100ByResolvedOrderByIdDesc(false) else repository.findTop100ByOrderByIdDesc()
        return logs.map { view(it, withStack = false) }
    }

    fun find(adminId: Long, errorCode: String): AdminErrorLogResDto {
        admins.require(adminId)
        return view(findLog(errorCode), withStack = true)
    }

    @Transactional
    fun resolve(adminId: Long, errorCode: String): AdminErrorLogResDto {
        admins.require(adminId)
        val log = findLog(errorCode)
        log.resolve()
        // 수정자·수정일시는 저장(flush) 때 채워지므로, 응답에 담기 전에 먼저 저장한다
        repository.flush()
        return view(log, withStack = false)
    }

    private fun findLog(errorCode: String): AdminErrorLog =
        repository.findByErrorCode(errorCode.trim().uppercase()) ?: throw BusinessException(ErrorCode.NOT_FOUND)

    private fun view(log: AdminErrorLog, withStack: Boolean) = AdminErrorLogResDto(
        errorCode = log.errorCode,
        source = log.source,
        category = log.category,
        exceptionClass = log.exceptionClass,
        message = log.message,
        stackTrace = if (withStack) log.stackTrace else null,
        httpMethod = log.httpMethod,
        requestPath = log.requestPath,
        userId = log.userId,
        ipAddress = log.ipAddress,
        userAgent = log.userAgent,
        resolved = log.resolved,
        createdAt = log.createdDt,
        editedBy = log.editedUs,
        editedAt = log.editedDt,
    )
}
