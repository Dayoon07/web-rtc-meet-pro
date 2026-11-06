package com.webrtcmeetpro.plan.presentation

import com.webrtcmeetpro.common.web.userId
import com.webrtcmeetpro.plan.application.PlanService
import com.webrtcmeetpro.plan.application.PlanView
import com.webrtcmeetpro.plan.application.SubscriptionView
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ChangePlanRequest(@field:NotBlank val planCode: String)

@RestController
@RequestMapping("/api")
class PlanController(private val planService: PlanService) {

    @GetMapping("/plans")
    fun plans(): List<PlanView> = planService.listPlans()

    @GetMapping("/subscriptions/me")
    fun mySubscription(@AuthenticationPrincipal jwt: Jwt): SubscriptionView =
        planService.mySubscription(jwt.userId)

    @PutMapping("/subscriptions/me")
    fun changePlan(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: ChangePlanRequest): SubscriptionView =
        planService.changePlan(jwt.userId, request.planCode)
}
