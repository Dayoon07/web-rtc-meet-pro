package com.webrtcmeetpro.domain.admin.dto.res

import com.webrtcmeetpro.domain.admin.ErrorCategory
import com.webrtcmeetpro.domain.admin.ErrorSource
import java.time.LocalDateTime

/** 관리자 화면용 에러 기록. stackTrace 는 한 건 조회에서만 채운다 */
data class AdminErrorLogResDto(
    val errorCode: String,
    val source: ErrorSource,
    val category: ErrorCategory,
    val exceptionClass: String?,
    val message: String?,
    val stackTrace: String?,
    val httpMethod: String?,
    val requestPath: String?,
    val userId: Long?,
    val ipAddress: String?,
    val userAgent: String?,
    val resolved: Boolean,
    val createdAt: LocalDateTime,
    /** 마지막으로 바꾼 관리자 (해결 표시 등) */
    val editedBy: Long?,
    val editedAt: LocalDateTime,
)
