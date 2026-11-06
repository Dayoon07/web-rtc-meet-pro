package com.webrtcmeetpro.plan.infrastructure

import com.webrtcmeetpro.plan.domain.BillingKey
import com.webrtcmeetpro.plan.domain.BillingKeyRepository
import com.webrtcmeetpro.plan.domain.Payment
import com.webrtcmeetpro.plan.domain.PaymentRepository
import com.webrtcmeetpro.plan.domain.Plan
import com.webrtcmeetpro.plan.domain.PlanRepository
import com.webrtcmeetpro.plan.domain.Subscription
import com.webrtcmeetpro.plan.domain.SubscriptionRepository
import org.springframework.data.jpa.repository.JpaRepository

interface PlanJpaRepository : JpaRepository<Plan, Long>, PlanRepository

interface SubscriptionJpaRepository : JpaRepository<Subscription, Long>, SubscriptionRepository

interface PaymentJpaRepository : JpaRepository<Payment, Long>, PaymentRepository

interface BillingKeyJpaRepository : JpaRepository<BillingKey, Long>, BillingKeyRepository
