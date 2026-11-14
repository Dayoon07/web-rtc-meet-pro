package com.webrtcmeetpro.application.admin

import com.webrtcmeetpro.domain.admin.AdminErrorLog
import com.webrtcmeetpro.domain.admin.AdminErrorLogRepository
import com.webrtcmeetpro.domain.admin.ErrorCategory
import com.webrtcmeetpro.domain.admin.ErrorSource
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/** 비동기 기록 스레드에서는 요청 객체를 쓸 수 없어서 요청 스레드에서 미리 뽑아 둔다 */
data class ErrorContext(
    val httpMethod: String?,
    val requestPath: String?,
    val userId: Long?,
    val ipAddress: String?,
    val userAgent: String?,
)

data class ClientErrorReport(
    val errorCode: String,
    val source: ErrorSource,
    val category: ErrorCategory,
    val name: String?,
    val message: String?,
    val stack: String?,
    /** 쿼리 문자열은 뺀다 */
    val path: String?,
)

/**
 * 응답을 늦추지 않게 비동기로 기록하고, 기록이 실패해도 요청에는 영향을 주지 않는다.
 * DB 가 꺼져서 난 에러는 기록도 실패하므로 서버 로그에는 항상 에러 코드와 함께 남긴다.
 */
@Service
class ErrorLogService(private val repository: AdminErrorLogRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun recordServerError(errorCode: String, category: ErrorCategory, e: Throwable, ctx: ErrorContext) {
        save(
            AdminErrorLog(
                errorCode = errorCode,
                source = ErrorSource.SERVER,
                category = category,
                exceptionClass = e.javaClass.name,
                message = ErrorClassifier.summary(e),
                stackTrace = e.stackTraceToString(),
                httpMethod = ctx.httpMethod,
                requestPath = ctx.requestPath,
                userId = ctx.userId,
                ipAddress = ctx.ipAddress,
                userAgent = ctx.userAgent,
            ),
        )
    }

    @Async
    fun recordClientError(report: ClientErrorReport, ctx: ErrorContext) {
        save(
            AdminErrorLog(
                errorCode = report.errorCode,
                source = report.source,
                category = report.category,
                exceptionClass = report.name,
                message = report.message,
                stackTrace = report.stack,
                httpMethod = null,
                requestPath = report.path,
                userId = ctx.userId,
                ipAddress = ctx.ipAddress,
                userAgent = ctx.userAgent,
            ),
        )
    }

    private fun save(entry: AdminErrorLog) {
        try {
            repository.save(entry)
        } catch (e: DataIntegrityViolationException) {
            // 화면이 재시도하면서 같은 에러 코드를 다시 보낸 경우
            log.debug("이미 기록된 에러 코드: {}", entry.errorCode)
        } catch (e: Exception) {
            log.warn("에러 기록 실패 [{}]: {}", entry.errorCode, e.toString())
        }
    }
}
