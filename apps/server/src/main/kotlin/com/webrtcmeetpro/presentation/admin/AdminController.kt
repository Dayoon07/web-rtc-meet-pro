package com.webrtcmeetpro.presentation.admin

import com.webrtcmeetpro.domain.admin.dto.req.AdminRefundReqDto
import com.webrtcmeetpro.domain.plan.dto.res.RefundQuoteResDto
import com.webrtcmeetpro.application.admin.AdminPaymentService
import com.webrtcmeetpro.domain.admin.dto.res.AdminPaymentResDto
import com.webrtcmeetpro.application.plan.RefundService
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus
import com.webrtcmeetpro.domain.admin.dto.res.PendingSignupResDto
import com.webrtcmeetpro.application.admin.AdminEmailVerificationService
import com.webrtcmeetpro.application.admin.AdminErrorLogService
import com.webrtcmeetpro.application.admin.SystemAdminAccess
import com.webrtcmeetpro.common.web.userId
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** SecurityConfig 에서 한 번, 서비스에서 한 번 더 시스템 관리자인지 확인한다 */
@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val errorLogs: AdminErrorLogService,
    private val admins: SystemAdminAccess,
    private val verifications: AdminEmailVerificationService,
    private val adminPayments: AdminPaymentService,
    private val refunds: RefundService,
) {

    @GetMapping("/payments")
    fun payments(@AuthenticationPrincipal jwt: Jwt, @RequestParam(required = false) q: String?, @RequestParam(required = false) status: String?): List<AdminPaymentResDto> {
        admins.require(jwt.userId)
        return adminPayments.recent(q, status)
    }

    @GetMapping("/payments/{orderId}/refund")
    fun refundQuote(@AuthenticationPrincipal jwt: Jwt, @PathVariable orderId: String): RefundQuoteResDto {
        admins.require(jwt.userId)
        return refunds.quoteForAdmin(orderId)
    }

    /** amount 가 없으면 약관대로 계산한 금액 */
    @PostMapping("/payments/{orderId}/refund")
    fun refund(@AuthenticationPrincipal jwt: Jwt, @PathVariable orderId: String, @RequestBody body: AdminRefundReqDto): RefundQuoteResDto {
        admins.require(jwt.userId)
        return refunds.refundByAdmin(orderId, jwt.userId, body.amount, body.reason)
    }

    @GetMapping("/signups/pending")
    fun pendingSignups(@AuthenticationPrincipal jwt: Jwt, @RequestParam(required = false) q: String?): List<PendingSignupResDto> {
        admins.require(jwt.userId)
        return verifications.pending(q)
    }

    @PostMapping("/users/{userId}/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun verifyEmail(@AuthenticationPrincipal jwt: Jwt, @PathVariable userId: Long) {
        admins.require(jwt.userId)
        verifications.approve(userId, jwt.userId)
    }

    @PostMapping("/users/{userId}/reject-signup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun rejectSignup(@AuthenticationPrincipal jwt: Jwt, @PathVariable userId: Long) {
        admins.require(jwt.userId)
        verifications.reject(userId, jwt.userId)
    }

    /** 관리자가 아니면 403 이라 관리자 사이트가 로그인을 막는다 */
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt) = admins.me(jwt.userId)

    @GetMapping("/error-logs")
    fun errorLogs(@AuthenticationPrincipal jwt: Jwt, @RequestParam(defaultValue = "false") unresolved: Boolean) =
        errorLogs.recent(jwt.userId, unresolved)

    @GetMapping("/error-logs/{errorCode}")
    fun errorLog(@AuthenticationPrincipal jwt: Jwt, @PathVariable errorCode: String) = errorLogs.find(jwt.userId, errorCode)

    @PostMapping("/error-logs/{errorCode}/resolve")
    fun resolve(@AuthenticationPrincipal jwt: Jwt, @PathVariable errorCode: String) = errorLogs.resolve(jwt.userId, errorCode)
}

