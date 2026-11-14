package com.webrtcmeetpro.presentation.common

import com.webrtcmeetpro.domain.common.dto.res.ErrorResDto
import com.webrtcmeetpro.application.admin.ErrorClassifier
import com.webrtcmeetpro.application.admin.ErrorContext
import com.webrtcmeetpro.application.admin.ErrorLogService
import com.webrtcmeetpro.application.chat.ChatService
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.common.web.ClientInfo
import com.webrtcmeetpro.domain.admin.AdminErrorLog
import com.webrtcmeetpro.domain.common.CurrentActor
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.web.ErrorResponse as SpringErrorResponse

@RestControllerAdvice
class GlobalExceptionHandler(
    private val errorLogs: ErrorLogService,
    private val currentActor: CurrentActor,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun respond(code: ErrorCode, params: Map<String, Any> = emptyMap(), field: String? = null, message: String = code.message) =
        ResponseEntity.status(code.status).body(ErrorResDto(code.name, message, params, field))

    /** 관리자 API 처럼 자세한 이유를 따로 준 경우만 그 메시지를 쓴다 */
    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException) = respond(e.errorCode, e.params, message = e.message ?: e.errorCode.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException) =
        respond(ErrorCode.INVALID_INPUT, field = e.bindingResult.fieldErrors.firstOrNull()?.field)

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun handleUnreadable(e: Exception) = respond(ErrorCode.INVALID_INPUT)

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadSize(e: MaxUploadSizeExceededException, request: HttpServletRequest) =
        if (request.requestURI.startsWith("/api/chat/")) {
            respond(ErrorCode.FILE_TOO_LARGE, mapOf("max" to ChatService.MAX_FILE_MB, "total" to ChatService.MAX_TOTAL_MB))
        } else {
            respond(ErrorCode.INVALID_IMAGE)
        }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(e: NoResourceFoundException) = respond(ErrorCode.NOT_FOUND)

    /** 사용자에게는 에러 코드와 대략적인 원인만 돌려주고, 자세한 내용은 서버 로그와 admin_error_logs 에 남긴다 */
    @ExceptionHandler(Exception::class)
    fun handleUnknown(e: Exception, request: HttpServletRequest): ResponseEntity<ErrorResDto>? {
        // 지원하지 않는 메서드·Content-Type 같은 잘못된 요청은 서버 오류가 아니다
        if (e is SpringErrorResponse && e.statusCode.is4xxClientError) {
            return ResponseEntity.status(e.statusCode).body(ErrorResDto(ErrorCode.INVALID_INPUT.name, ErrorCode.INVALID_INPUT.message))
        }
        // 연결이 이미 끊겼으면 보낼 곳도, 기록할 것도 없다
        if (ErrorClassifier.isClientAbort(e)) return null

        val errorCode = AdminErrorLog.newErrorCode()
        val category = ErrorClassifier.classify(e)
        log.error("[{}] {} {} 처리 중 예상하지 못한 오류 (분류: {})", errorCode, request.method, request.requestURI, category, e)

        val client = ClientInfo.from(request)
        errorLogs.recordServerError(
            errorCode,
            category,
            e,
            ErrorContext(request.method, request.requestURI, currentActor.userId(), client.ipAddress, client.userAgent),
        )
        val internal = ErrorCode.INTERNAL_ERROR
        return ResponseEntity.status(internal.status)
            .body(ErrorResDto(internal.name, internal.message, errorCode = errorCode, reason = category.name))
    }
}
