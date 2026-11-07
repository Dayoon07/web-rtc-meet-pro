package com.webrtcmeetpro.infrastructure.plan

import com.webrtcmeetpro.domain.plan.AiUsage
import com.webrtcmeetpro.domain.plan.AiUsageRepository
import com.webrtcmeetpro.domain.plan.BillingKey
import com.webrtcmeetpro.domain.plan.BillingKeyRepository
import com.webrtcmeetpro.domain.plan.Payment
import com.webrtcmeetpro.domain.plan.PaymentRepository
import com.webrtcmeetpro.domain.plan.Plan
import com.webrtcmeetpro.domain.plan.PlanRepository
import com.webrtcmeetpro.domain.plan.Subscription
import com.webrtcmeetpro.domain.plan.SubscriptionRepository
import org.springframework.data.jpa.repository.JpaRepository

interface PlanJpaRepository : JpaRepository<Plan, Long>, PlanRepository

interface SubscriptionJpaRepository : JpaRepository<Subscription, Long>, SubscriptionRepository

interface PaymentJpaRepository : JpaRepository<Payment, Long>, PaymentRepository

interface BillingKeyJpaRepository : JpaRepository<BillingKey, Long>, BillingKeyRepository

interface AiUsageJpaRepository : JpaRepository<AiUsage, Long>, AiUsageRepository
