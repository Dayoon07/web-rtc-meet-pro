package com.webrtcmeetpro.domain.admin.dto.res

import com.webrtcmeetpro.domain.admin.ErrorCategory
import com.webrtcmeetpro.domain.admin.ErrorSource
import java.time.LocalDateTime

/** stackTrace 는 크니까 한 건 조회에서만 채운다 */
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
    val editedBy: Long?,
    val editedAt: LocalDateTime,
)
