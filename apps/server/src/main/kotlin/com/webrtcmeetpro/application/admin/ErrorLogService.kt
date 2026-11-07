package com.webrtcmeetpro.application.admin

import com.webrtcmeetpro.domain.admin.AdminErrorLog
import com.webrtcmeetpro.domain.admin.AdminErrorLogRepository
import com.webrtcmeetpro.domain.admin.ErrorCategory
import com.webrtcmeetpro.domain.admin.ErrorSource
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/** 에러를 만난 요청 정보. 비동기 기록 스레드에서는 요청 객체를 쓸 수 없으므로 요청 스레드에서 미리 뽑아 둔다 */
data class ErrorContext(
    val httpMethod: String?,
    val requestPath: String?,
    val userId: Long?,
    val ipAddress: String?,
    val userAgent: String?,
)

/** 웹·앱이 보낸 화면 오류 */
data class ClientErrorReport(
    val errorCode: String,
    val source: ErrorSource,
    val category: ErrorCategory,
    val name: String?,
    val message: String?,
    val stack: String?,
    /** 오류가 난 화면 경로 (쿼리 문자열 제외) */
    val path: String?,
)

/**
 * admin_error_logs 기록. 응답을 늦추지 않도록 비동기로 쓰고, 기록이 실패해도 서비스 요청에는 영향을 주지 않는다
 * (DB 가 꺼져서 난 에러면 기록도 실패한다 → 서버 로그에는 항상 에러 코드와 함께 남긴다).
 * 트랜잭션은 repository.save 가 따로 연다.
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
            // 같은 에러 코드를 다시 보낸 경우 (화면이 재시도) — 이미 기록돼 있다
            log.debug("이미 기록된 에러 코드: {}", entry.errorCode)
        } catch (e: Exception) {
            log.warn("에러 기록 실패 [{}]: {}", entry.errorCode, e.toString())
        }
    }
}
