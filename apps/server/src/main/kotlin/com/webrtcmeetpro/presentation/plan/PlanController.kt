package com.webrtcmeetpro.presentation.plan

import com.webrtcmeetpro.domain.plan.dto.req.AutoRenewReqDto
import com.webrtcmeetpro.domain.plan.dto.req.OverageReqDto
import com.webrtcmeetpro.domain.plan.dto.req.PurchaseReqDto
import com.webrtcmeetpro.domain.plan.dto.req.RefundReqDto
import com.webrtcmeetpro.domain.plan.dto.req.RegisterCardReqDto
import com.webrtcmeetpro.domain.plan.dto.res.CardRegistrationResDto
import com.webrtcmeetpro.domain.plan.dto.res.CardResDto
import com.webrtcmeetpro.domain.plan.dto.res.ExpiryNoticeResDto
import com.webrtcmeetpro.domain.plan.dto.res.PaymentResDto
import com.webrtcmeetpro.domain.plan.dto.res.PlanResDto
import com.webrtcmeetpro.domain.plan.dto.res.RefundQuoteResDto
import com.webrtcmeetpro.domain.plan.dto.res.SubscriptionResDto
import com.webrtcmeetpro.application.plan.RefundService
import com.webrtcmeetpro.application.plan.BillingService
import com.webrtcmeetpro.application.plan.MemberOverageService
import com.webrtcmeetpro.application.plan.PlanService
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.common.web.userId
import com.webrtcmeetpro.domain.plan.BillingCycle
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class PlanController(
    private val planService: PlanService,
    private val billingService: BillingService,
    private val overageService: MemberOverageService,
    private val refunds: RefundService,
) {

    @GetMapping("/teams/{teamId}/overage")
    fun overage(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = overageService.view(teamId, jwt.userId)

    @PutMapping("/teams/{teamId}/overage")
    fun setOverage(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @RequestBody body: OverageReqDto) =
        overageService.setAllowed(teamId, jwt.userId, body.allowed, body.agree)

    @GetMapping("/plans")
    fun plans(): List<PlanResDto> = planService.listPlans()

    @GetMapping("/teams/{teamId}/subscription")
    fun workspaceSubscription(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long): SubscriptionResDto =
        planService.workspaceSubscription(teamId, jwt.userId)

    @PostMapping("/teams/{teamId}/subscription/purchase")
    fun purchase(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody request: PurchaseReqDto): SubscriptionResDto {
        if (!request.agree) throw BusinessException(ErrorCode.PAYMENT_CONSENT_REQUIRED)
        return billingService.purchase(jwt.userId, teamId, request.planCode, request.cycle, request.autoRenew)
    }

    @GetMapping("/subscriptions/notices")
    fun expiryNotices(@AuthenticationPrincipal jwt: Jwt): List<ExpiryNoticeResDto> = planService.expiryNotices(jwt.userId)

    @PatchMapping("/subscriptions/{id}")
    fun setAutoRenew(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long, @RequestBody request: AutoRenewReqDto): SubscriptionResDto =
        planService.setAutoRenew(jwt.userId, id, request.autoRenew)

    @PostMapping("/subscriptions/{id}/acknowledge-expiry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun acknowledgeExpiry(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = planService.acknowledgeExpiry(jwt.userId, id)

    @PostMapping("/billing/card/prepare")
    fun prepareCard(@AuthenticationPrincipal jwt: Jwt): CardRegistrationResDto = billingService.prepareCard(jwt.userId)

    @PostMapping("/billing/card")
    fun registerCard(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: RegisterCardReqDto): CardResDto =
        billingService.registerCard(jwt.userId, request.customerKey, request.authKey)

    @DeleteMapping("/billing/card")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeCard(@AuthenticationPrincipal jwt: Jwt) = billingService.removeCard(jwt.userId)

    @GetMapping("/billing/payments")
    fun payments(@AuthenticationPrincipal jwt: Jwt): List<PaymentResDto> = billingService.history(jwt.userId)

    @GetMapping("/billing/payments/{orderId}/refund")
    fun refundQuote(@AuthenticationPrincipal jwt: Jwt, @PathVariable orderId: String): RefundQuoteResDto = refunds.quoteForUser(orderId, jwt.userId)

    @PostMapping("/billing/payments/{orderId}/refund")
    fun refund(@AuthenticationPrincipal jwt: Jwt, @PathVariable orderId: String, @Valid @RequestBody request: RefundReqDto): RefundQuoteResDto {
        if (request.agree != true) throw BusinessException(ErrorCode.REFUND_NOT_ALLOWED, mapOf("reason" to "AGREE_REQUIRED"))
        return refunds.refundByUser(orderId, jwt.userId, request.reason)
    }
}

