package com.webrtcmeetpro.presentation.common

import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
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

/** params 는 번역 문구 보간용 ({{max}} 등) */
data class ErrorResponse(
    val code: String,
    val message: String,
    val params: Map<String, Any> = emptyMap(),
    val field: String? = null,
)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun respond(code: ErrorCode, params: Map<String, Any> = emptyMap(), field: String? = null) =
        ResponseEntity.status(code.status).body(ErrorResponse(code.name, code.message, params, field))

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException) = respond(e.errorCode, e.params)

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
    fun handleUploadSize(e: MaxUploadSizeExceededException) = respond(ErrorCode.INVALID_IMAGE)

    /** 없는 정적 파일(/uploads/..)이나 API 경로 */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(e: NoResourceFoundException) = respond(ErrorCode.NOT_FOUND)

    @ExceptionHandler(Exception::class)
    fun handleUnknown(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", e)
        return respond(ErrorCode.INTERNAL_ERROR)
    }
}
