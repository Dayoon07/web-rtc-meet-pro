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

/** 권한을 회수하면 바로 막히도록 토큰이 아니라 매번 DB 에서 관리자 여부를 읽는다 */
@Component
class SystemAdminAccess(private val userRepository: UserRepository) {

    fun isSystemAdmin(userId: Long): Boolean = userRepository.findUserById(userId)?.isSystemAdmin == true

    fun require(userId: Long) {
        if (!isSystemAdmin(userId)) throw BusinessException(ErrorCode.FORBIDDEN)
    }

    fun me(userId: Long): AdminMeResDto {
        val user = userRepository.findUserById(userId)?.takeIf { it.isSystemAdmin } ?: throw BusinessException(ErrorCode.FORBIDDEN)
        return AdminMeResDto(user.id!!, user.email, user.nickname)
    }
}

@Service
@Transactional(readOnly = true)
class AdminErrorLogService(
    private val repository: AdminErrorLogRepository,
    private val admins: SystemAdminAccess,
) {

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
        // 수정자·수정일시는 flush 때 채워지므로 응답에 담기 전에 먼저 저장한다
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
