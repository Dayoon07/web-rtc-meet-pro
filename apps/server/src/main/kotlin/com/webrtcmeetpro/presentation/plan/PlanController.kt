package com.webrtcmeetpro.presentation.plan

import com.webrtcmeetpro.application.plan.BillingService
import com.webrtcmeetpro.application.plan.CardRegistrationView
import com.webrtcmeetpro.application.plan.CardView
import com.webrtcmeetpro.application.plan.PaymentView
import com.webrtcmeetpro.application.plan.PlanService
import com.webrtcmeetpro.application.plan.PlanView
import com.webrtcmeetpro.application.plan.SubscriptionView
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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class PurchaseRequest(@field:NotBlank val planCode: String, val cycle: BillingCycle, val autoRenew: Boolean)

data class AutoRenewRequest(val autoRenew: Boolean)

data class RegisterCardRequest(
    @field:NotBlank @field:Size(max = 50) val customerKey: String,
    @field:NotBlank @field:Size(max = 300) val authKey: String,
)

@RestController
@RequestMapping("/api")
class PlanController(private val planService: PlanService, private val billingService: BillingService) {

    @GetMapping("/plans")
    fun plans(): List<PlanView> = planService.listPlans()

    @GetMapping("/subscriptions/me")
    fun mySubscription(@AuthenticationPrincipal jwt: Jwt): SubscriptionView = planService.mySubscription(jwt.userId)

    /** 등록한 카드로 결제 (월 / 연). 성공하면 바뀐 구독 상태 */
    @PostMapping("/subscriptions/purchase")
    fun purchase(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: PurchaseRequest): SubscriptionView =
        billingService.purchase(jwt.userId, request.planCode, request.cycle, request.autoRenew)

    @PatchMapping("/subscriptions/{id}")
    fun setAutoRenew(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long, @RequestBody request: AutoRenewRequest): SubscriptionView =
        planService.setAutoRenew(jwt.userId, id, request.autoRenew)

    /** "플랜이 만료됐어요" 창 닫기 */
    @PostMapping("/subscriptions/{id}/acknowledge-expiry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun acknowledgeExpiry(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: Long) = planService.acknowledgeExpiry(jwt.userId, id)

    /** 토스 카드 등록창을 열 값 (clientKey, customerKey) */
    @PostMapping("/billing/card/prepare")
    fun prepareCard(@AuthenticationPrincipal jwt: Jwt): CardRegistrationView = billingService.prepareCard(jwt.userId)

    /** 카드 등록창에서 돌아온 authKey 로 카드 등록 (전에 등록한 카드는 바뀐다) */
    @PostMapping("/billing/card")
    fun registerCard(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: RegisterCardRequest): CardView =
        billingService.registerCard(jwt.userId, request.customerKey, request.authKey)

    @DeleteMapping("/billing/card")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeCard(@AuthenticationPrincipal jwt: Jwt) = billingService.removeCard(jwt.userId)

    @GetMapping("/billing/payments")
    fun payments(@AuthenticationPrincipal jwt: Jwt): List<PaymentView> = billingService.history(jwt.userId)
}
