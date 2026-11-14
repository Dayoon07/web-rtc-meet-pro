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

    /** 메시지를 따로 준 경우(관리자 API 의 자세한 이유 등)는 그 메시지를, 아니면 에러 코드의 기본 메시지를 보낸다 */
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

    /** 채팅 첨부파일은 파일 크기 안내, 그 밖(프로필 사진)은 이미지 안내 */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadSize(e: MaxUploadSizeExceededException, request: HttpServletRequest) =
        if (request.requestURI.startsWith("/api/chat/")) {
            respond(ErrorCode.FILE_TOO_LARGE, mapOf("max" to ChatService.MAX_FILE_MB, "total" to ChatService.MAX_TOTAL_MB))
        } else {
            respond(ErrorCode.INVALID_IMAGE)
        }

    /** 없는 정적 파일(/uploads/..)이나 API 경로 */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(e: NoResourceFoundException) = respond(ErrorCode.NOT_FOUND)

    /**
     * 예상하지 못한 에러: 에러 코드를 새로 만들어 서버 로그 + admin_error_logs 에 남기고,
     * 사용자에게는 에러 코드와 대략적인 원인만 돌려준다.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnknown(e: Exception, request: HttpServletRequest): ResponseEntity<ErrorResDto>? {
        // 잘못된 요청 형식(지원하지 않는 메서드·Content-Type, 빠진 multipart 파트 등)은 서버 오류가 아니다
        if (e is SpringErrorResponse && e.statusCode.is4xxClientError) {
            return ResponseEntity.status(e.statusCode).body(ErrorResDto(ErrorCode.INVALID_INPUT.name, ErrorCode.INVALID_INPUT.message))
        }
        // 사용자가 내려받기를 취소하는 등 연결이 이미 끊겼으면 보낼 곳도, 기록할 것도 없다
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
