package com.webrtcmeetpro.presentation.admin

import com.webrtcmeetpro.application.admin.AdminPaymentService
import com.webrtcmeetpro.application.admin.AdminPaymentView
import com.webrtcmeetpro.application.plan.RefundQuoteView
import com.webrtcmeetpro.application.plan.RefundService
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.http.HttpStatus
import com.webrtcmeetpro.application.admin.PendingSignupView
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

/**
 * 관리자 API (/api/admin 아래 전부). 시스템 관리자만 — SecurityConfig 에서 한 번, 서비스에서 한 번 더 확인한다.
 * 관리자 사이트(별도 서브도메인)가 이 API 를 쓴다.
 */
@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val errorLogs: AdminErrorLogService,
    private val admins: SystemAdminAccess,
    private val verifications: AdminEmailVerificationService,
    private val adminPayments: AdminPaymentService,
    private val refunds: RefundService,
) {

    /** 최근 결제 200건. q = 주문번호·이메일·주문명·팀 이름, status = DONE | CANCELED | FAILED ... */
    @GetMapping("/payments")
    fun payments(@AuthenticationPrincipal jwt: Jwt, @RequestParam(required = false) q: String?, @RequestParam(required = false) status: String?): List<AdminPaymentView> {
        admins.require(jwt.userId)
        return adminPayments.recent(q, status)
    }

    /** 환불 미리 계산 (약관대로 계산한 금액) */
    @GetMapping("/payments/{orderId}/refund")
    fun refundQuote(@AuthenticationPrincipal jwt: Jwt, @PathVariable orderId: String): RefundQuoteView {
        admins.require(jwt.userId)
        return refunds.quoteForAdmin(orderId)
    }

    /** 관리자 환불: amount 가 없으면 약관대로 계산한 금액. 그 플랜은 바로 끝난다 */
    @PostMapping("/payments/{orderId}/refund")
    fun refund(@AuthenticationPrincipal jwt: Jwt, @PathVariable orderId: String, @RequestBody body: AdminRefundRequest): RefundQuoteView {
        admins.require(jwt.userId)
        return refunds.refundByAdmin(orderId, jwt.userId, body.amount, body.reason)
    }

    /** 이메일 인증을 기다리는 가입 (최근 200명). q = 이메일·닉네임·이름 일부 */
    @GetMapping("/signups/pending")
    fun pendingSignups(@AuthenticationPrincipal jwt: Jwt, @RequestParam(required = false) q: String?): List<PendingSignupView> {
        admins.require(jwt.userId)
        return verifications.pending(q)
    }

    /** 이메일 인증 강제 승인 */
    @PostMapping("/users/{userId}/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun verifyEmail(@AuthenticationPrincipal jwt: Jwt, @PathVariable userId: Long) {
        admins.require(jwt.userId)
        verifications.approve(userId, jwt.userId)
    }

    /** 이메일 인증 거절 = 가입 취소 (개인정보 지우고 계정 닫기) */
    @PostMapping("/users/{userId}/reject-signup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun rejectSignup(@AuthenticationPrincipal jwt: Jwt, @PathVariable userId: Long) {
        admins.require(jwt.userId)
        verifications.reject(userId, jwt.userId)
    }

    /** 로그인한 관리자 (관리자가 아니면 403 → 관리자 사이트가 로그인을 막는다) */
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt) = admins.me(jwt.userId)

    /** 최근 에러 100건. unresolved=true 면 해결 표시가 안 된 것만 */
    @GetMapping("/error-logs")
    fun errorLogs(@AuthenticationPrincipal jwt: Jwt, @RequestParam(defaultValue = "false") unresolved: Boolean) =
        errorLogs.recent(jwt.userId, unresolved)

    /** 사용자가 알려 준 에러 코드(ERR-...)로 찾기. 스택 포함 */
    @GetMapping("/error-logs/{errorCode}")
    fun errorLog(@AuthenticationPrincipal jwt: Jwt, @PathVariable errorCode: String) = errorLogs.find(jwt.userId, errorCode)

    @PostMapping("/error-logs/{errorCode}/resolve")
    fun resolve(@AuthenticationPrincipal jwt: Jwt, @PathVariable errorCode: String) = errorLogs.resolve(jwt.userId, errorCode)
}

/** 관리자 환불: amount = 환불 금액 (없으면 약관대로 계산한 금액), reason = 사유 */
data class AdminRefundRequest(val amount: Long? = null, val reason: String? = null)
