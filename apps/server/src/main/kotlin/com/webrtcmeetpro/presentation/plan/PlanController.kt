package com.webrtcmeetpro.presentation.plan

import com.webrtcmeetpro.application.plan.BillingService
import com.webrtcmeetpro.application.plan.MemberOverageService
import com.webrtcmeetpro.application.plan.CardRegistrationView
import com.webrtcmeetpro.application.plan.CardView
import com.webrtcmeetpro.application.plan.ExpiryNoticeView
import com.webrtcmeetpro.application.plan.PaymentView
import com.webrtcmeetpro.application.plan.PlanService
import com.webrtcmeetpro.application.plan.PlanView
import com.webrtcmeetpro.application.plan.SubscriptionView
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

/** agree: 주문 내용·자동결제·환불 규정을 확인하고 결제에 동의 (전자상거래법 제8조) */
data class PurchaseRequest(@field:NotBlank val planCode: String, val cycle: BillingCycle, val autoRenew: Boolean, val agree: Boolean = false)

data class AutoRenewRequest(val autoRenew: Boolean)

/** 한도 초과 허용. 켤 때는 안내(1명당 월 요금, 결제일 멤버 수 기준)에 동의(agree = true)해야 한다 */
data class OverageRequest(val allowed: Boolean, val agree: Boolean = false)

data class RegisterCardRequest(
    @field:NotBlank @field:Size(max = 50) val customerKey: String,
    @field:NotBlank @field:Size(max = 300) val authKey: String,
)

@RestController
@RequestMapping("/api")
class PlanController(
    private val planService: PlanService,
    private val billingService: BillingService,
    private val overageService: MemberOverageService,
) {

    /** 멤버 한도 초과: 지금 인원·초과 인원·다음 결제 금액 (워크스페이스 멤버) */
    @GetMapping("/teams/{teamId}/overage")
    fun overage(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long) = overageService.view(teamId, jwt.userId)

    /** 한도 초과 허용 켜기·끄기 (소유자) */
    @PutMapping("/teams/{teamId}/overage")
    fun setOverage(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @RequestBody body: OverageRequest) =
        overageService.setAllowed(teamId, jwt.userId, body.allowed, body.agree)

    @GetMapping("/plans")
    fun plans(): List<PlanView> = planService.listPlans()

    /** 워크스페이스(팀)의 요금제 상태. 멤버면 볼 수 있다 */
    @GetMapping("/teams/{teamId}/subscription")
    fun workspaceSubscription(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long): SubscriptionView =
        planService.workspaceSubscription(teamId, jwt.userId)

    /** 워크스페이스 플랜 결제 (소유자만, 등록한 카드로 월 / 연). 성공하면 바뀐 구독 상태 */
    @PostMapping("/teams/{teamId}/subscription/purchase")
    fun purchase(@AuthenticationPrincipal jwt: Jwt, @PathVariable teamId: Long, @Valid @RequestBody request: PurchaseRequest): SubscriptionView {
        if (!request.agree) throw BusinessException(ErrorCode.PAYMENT_CONSENT_REQUIRED)
        return billingService.purchase(jwt.userId, teamId, request.planCode, request.cycle, request.autoRenew)
    }

    /** 내가 결제한 구독 중 아직 확인하지 않은 만료 ("플랜이 만료됐어요" 창) */
    @GetMapping("/subscriptions/notices")
    fun expiryNotices(@AuthenticationPrincipal jwt: Jwt): List<ExpiryNoticeView> = planService.expiryNotices(jwt.userId)

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
