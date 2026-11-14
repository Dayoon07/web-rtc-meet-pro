package com.webrtcmeetpro.presentation.common

import com.webrtcmeetpro.domain.admin.dto.req.ClientErrorReqDto
import com.webrtcmeetpro.application.admin.ClientErrorReport
import com.webrtcmeetpro.application.admin.ErrorContext
import com.webrtcmeetpro.application.admin.ErrorLogService
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.common.web.ClientInfo
import com.webrtcmeetpro.domain.admin.ErrorCategory
import com.webrtcmeetpro.domain.admin.ErrorSource
import com.webrtcmeetpro.domain.common.CurrentActor
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RestController
class ClientErrorController(
    private val errorLogs: ErrorLogService,
    private val currentActor: CurrentActor,
    private val limiter: ClientErrorRateLimiter,
) {

    @PostMapping("/api/client-errors")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun report(@Valid @RequestBody req: ClientErrorReqDto, request: HttpServletRequest) {
        val source = req.source!!
        if (source == ErrorSource.SERVER || req.category !in CLIENT_CATEGORIES) throw BusinessException(ErrorCode.INVALID_INPUT)
        val client = ClientInfo.from(request)
        // 화면이 오류를 반복해서 보내도 테이블이 넘치지 않게 (넘친 신고는 조용히 버린다)
        if (!limiter.tryAcquire(client.ipAddress ?: "unknown")) return

        errorLogs.recordClientError(
            ClientErrorReport(
                errorCode = req.errorCode!!,
                source = source,
                category = req.category,
                name = req.name,
                message = req.message,
                stack = req.stack,
                path = req.path?.substringBefore('?'),
            ),
            ErrorContext(null, null, currentActor.userId(), client.ipAddress, client.userAgent),
        )
    }

    companion object {
        private val CLIENT_CATEGORIES = setOf(ErrorCategory.CLIENT, ErrorCategory.NETWORK)
    }
}

/** IP 하나당 1분에 20건 (고정 창). 서버 한 대 기준의 메모리 카운터 */
@Component
class ClientErrorRateLimiter {

    private class Window(val startedAt: Long) {
        val count = AtomicInteger()
    }

    private val windows = ConcurrentHashMap<String, Window>()

    fun tryAcquire(key: String, now: Long = System.currentTimeMillis()): Boolean {
        if (windows.size > MAX_KEYS) windows.entries.removeIf { now - it.value.startedAt > WINDOW_MS }
        val window = windows.compute(key) { _, old -> if (old == null || now - old.startedAt > WINDOW_MS) Window(now) else old }!!
        return window.count.incrementAndGet() <= LIMIT
    }

    companion object {
        const val WINDOW_MS = 60_000L
        const val LIMIT = 20
        private const val MAX_KEYS = 10_000
    }
}
