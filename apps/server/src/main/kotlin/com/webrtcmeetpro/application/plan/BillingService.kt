package com.webrtcmeetpro.application.plan

import com.webrtcmeetpro.domain.plan.dto.res.CardRegistrationResDto
import com.webrtcmeetpro.domain.plan.dto.res.CardResDto
import com.webrtcmeetpro.domain.plan.dto.res.PaymentResDto
import com.webrtcmeetpro.domain.plan.dto.res.SubscriptionResDto
import com.webrtcmeetpro.application.mail.MailLanguage
import com.webrtcmeetpro.application.mail.MailService
import com.webrtcmeetpro.application.mail.MailTemplates
import com.webrtcmeetpro.application.mail.PlanMailKind
import com.webrtcmeetpro.application.notification.NotificationService
import com.webrtcmeetpro.common.error.BusinessException
import com.webrtcmeetpro.common.error.ErrorCode
import com.webrtcmeetpro.config.JacksonConfig
import com.webrtcmeetpro.config.properties.TossProperties
import com.webrtcmeetpro.config.properties.WebProperties
import com.webrtcmeetpro.domain.notification.NotificationType
import com.webrtcmeetpro.domain.plan.BillingCycle
import com.webrtcmeetpro.domain.plan.BillingKey
import com.webrtcmeetpro.domain.plan.BillingKeyRepository
import com.webrtcmeetpro.domain.plan.Payment
import com.webrtcmeetpro.domain.plan.PaymentKind
import com.webrtcmeetpro.domain.team.Team
import com.webrtcmeetpro.domain.plan.PaymentRepository
import com.webrtcmeetpro.domain.plan.PaymentStatus
import com.webrtcmeetpro.domain.plan.Plan
import com.webrtcmeetpro.domain.plan.Subscription
import com.webrtcmeetpro.domain.plan.SubscriptionRepository
import com.webrtcmeetpro.domain.plan.SubscriptionStatus
import com.webrtcmeetpro.domain.user.UserRepository
import com.webrtcmeetpro.domain.team.TeamRepository
import com.webrtcmeetpro.domain.team.TeamStatus
import com.webrtcmeetpro.external.toss.TossException
import com.webrtcmeetpro.external.toss.TossPaymentsClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

/**
 * 토스페이먼츠 자동결제(빌링).
 * 1) 카드 등록: 웹이 토스 카드 등록창을 띄우고, 돌아온 authKey 로 빌링키를 받아 암호화해 저장한다.
 * 2) 결제: 등록한 카드(빌링키)로 바로 결제하고, 성공하면 구독 기간을 늘린다 (월 = 월 가격, 연 = 월 가격 × 12 × 0.9).
 * 3) 갱신(스케줄러): 기간이 끝난 구독은 자동결제면 다시 결제하고, 아니면 만료. 실패하면 하루 간격으로 3번까지 다시 시도.
 *
 * 외부 결제 호출은 DB 트랜잭션 밖에서 한다 (결제 기록이 롤백돼 사라지지 않게, 오래 걸리는 호출이 DB 를 붙잡지 않게).
 */
@Service
class BillingService(
    private val toss: TossPaymentsClient,
    private val tossProperties: TossProperties,
    private val cipher: BillingKeyCipher,
    private val planService: PlanService,
    private val billingKeys: BillingKeyRepository,
    private val payments: PaymentRepository,
    private val subscriptions: SubscriptionRepository,
    private val users: UserRepository,
    private val teams: TeamRepository,
    private val notifications: NotificationService,
    private val mail: MailService,
    private val web: WebProperties,
    private val overage: MemberOverageService,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    // ----------------------------------------------------------------- 카드

    fun prepareCard(userId: Long): CardRegistrationResDto {
        requireEnabled()
        val user = users.findUserById(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        return CardRegistrationResDto(tossProperties.clientKey, cipher.newCustomerKey(userId), user.email, user.realName ?: user.nickname)
    }

    /** 카드 등록창에서 돌아옴 → 빌링키 발급. 전에 등록한 카드는 해지 (한 사람당 카드 하나) */
    fun registerCard(userId: Long, customerKey: String, authKey: String): CardResDto {
        requireEnabled()
        if (!cipher.isOwnCustomerKey(userId, customerKey)) throw BusinessException(ErrorCode.BILLING_CARD_INVALID)
        val issued = try {
            toss.issueBillingKey(authKey, customerKey)
        } catch (e: TossException) {
            throw BusinessException(ErrorCode.BILLING_CARD_FAILED, mapOf("reason" to e.message), e.message)
        }
        return tx.execute {
            billingKeys.findAllByUserIdAndActiveTrue(userId).forEach { it.revoke() }
            val saved = billingKeys.save(
                BillingKey(userId, customerKey, cipher.encrypt(issued.billingKey), issued.cardCompany?.take(30), issued.cardNumber?.take(30)),
            )
            planService.cardView(saved)
        }!!.also { log.info("카드 등록 (userId={}, {} {})", userId, it.company, it.numberMasked) }
    }

    /** 카드 삭제. 자동결제할 카드가 없어지므로 모든 구독의 자동결제도 끈다 (기간 끝까지는 계속 쓸 수 있다) */
    fun removeCard(userId: Long) {
        tx.executeWithoutResult {
            billingKeys.findAllByUserIdAndActiveTrue(userId).forEach { it.revoke() }
            subscriptions.findAllByUserIdAndStatusIn(userId, listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE))
                .forEach { it.autoRenew = false }
        }
    }

    // ----------------------------------------------------------------- 결제

    /**
     * 워크스페이스 플랜 결제 (소유자만). 같은 플랜을 이미 쓰고 있으면 남은 기간 뒤로 늘어나고, 다른 플랜이면 따로 새 구독이 생긴다
     * (가장 높은 플랜이 적용되고, 그게 끝나면 남은 낮은 플랜으로).
     */
    fun purchase(userId: Long, teamId: Long, planCode: String, cycle: BillingCycle, autoRenew: Boolean): SubscriptionResDto {
        requireEnabled()
        val team = planService.requireOwnedTeam(teamId, userId)
        val plan = planService.findPlan(planCode)
        if (plan.isFree) throw BusinessException(ErrorCode.PLAN_NOT_PURCHASABLE)
        val card = billingKeys.findByUserIdAndActiveTrue(userId) ?: throw BusinessException(ErrorCode.BILLING_CARD_REQUIRED)

        val payment = tx.execute { payments.save(newPayment(userId, teamId, team.name, plan, cycle, card)) }!!
        try {
            chargeAndApply(payment.orderId, card) { now -> planService.applyPurchase(teamId, userId, plan, cycle, autoRenew, now) }
            log.info("결제 성공 (userId={}, {} {}, {}원, orderId={})", userId, plan.code, cycle, payment.amount, payment.orderId)
        } catch (e: TossException) {
            if (e.uncertain) {
                log.error("결제 결과를 모름 (orderId={}): 토스 상점관리자에서 확인 필요", payment.orderId)
                throw BusinessException(ErrorCode.PAYMENT_UNCERTAIN)
            }
            log.info("결제 거절 (userId={}, orderId={}): {} {}", userId, payment.orderId, e.code, e.message)
            throw BusinessException(ErrorCode.PAYMENT_FAILED, mapOf("reason" to e.message), e.message)
        }
        return planService.workspaceSubscription(teamId, userId)
    }

    fun history(userId: Long): List<PaymentResDto> {
        val plans = mutableMapOf<Long, Plan>()
        val teamNames = mutableMapOf<Long, String?>()
        return payments.findTop50ByUserIdOrderByCreatedDtDesc(userId).map {
            val plan = plans.getOrPut(it.planId) { planService.planById(it.planId) }
            val supply = Math.round(it.amount * 10.0 / 11.0)
            PaymentResDto(
                orderId = it.orderId,
                orderName = it.orderName,
                planCode = plan.code,
                planName = plan.name,
                amount = it.amount,
                supplyAmount = supply,
                vat = it.amount - supply,
                status = it.status,
                billingCycle = it.billingCycle,
                method = it.method,
                cardCompany = it.cardCompany,
                cardNumberMasked = it.cardNumberMasked,
                approveNo = it.approveNo,
                receiptUrl = it.receiptUrl,
                failureMessage = it.failureMessage,
                approvedAt = it.approvedAt,
                periodEnd = it.periodEnd,
                createdAt = it.createdDt,
                teamId = it.teamId,
                teamName = it.teamId?.let { id -> teamNames.getOrPut(id) { teams.findTeamById(id)?.name } },
                kind = it.paymentKind,
                overageMembers = it.overageMembers,
                overageAmount = it.overageAmount,
                refundedAmount = it.refundedAmount,
                refundedAt = it.canceledAt.takeIf { _ -> it.refundedAmount > 0 },
                refundReason = it.refundReason,
            )
        }
    }

    // ----------------------------------------------------------------- 갱신 (스케줄러)

    /**
     * - 자동결제가 꺼진 구독: 끝나기 3일 전에 알림, 끝나면 만료
     * - 자동결제 구독: 끝나면 결제. 실패하면 하루 뒤 다시 (3번 실패하면 만료)
     * @return 처리한 구독 수
     */
    fun processRenewals(now: LocalDateTime = LocalDateTime.now()): Int {
        val candidates = subscriptions.findAllByStatusInAndCurrentPeriodEndBefore(
            listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE),
            now.plusDays(EXPIRING_NOTICE_DAYS),
        )
        var handled = 0
        for (s in candidates) {
            runCatching {
                when {
                    s.status == SubscriptionStatus.ACTIVE && s.currentPeriodEnd!!.isAfter(now) -> {
                        if (s.expiringNotifiedAt == null && !willRenew(s)) {
                            tx.executeWithoutResult { notifyExpiring(reload(s), now) }
                            handled++
                        } else if (willRenew(s)) {
                            // 갱신 결제에 초과 인원 요금이 들어가면 3일 전에 금액 안내
                            tx.executeWithoutResult { noticeRenewalOverage(reload(s), now) }
                        }
                    }
                    s.status == SubscriptionStatus.PAST_DUE && s.nextRetryAt?.isAfter(now) == true -> Unit
                    else -> {
                        renew(s, now)
                        handled++
                    }
                }
            }.onFailure { log.error("구독 갱신 처리 실패 (subscriptionId={})", s.id, it) }
        }
        return handled
    }

    private fun renew(s: Subscription, now: LocalDateTime) {
        val card = billingKeys.findByUserIdAndActiveTrue(s.userId)
        val plan = planService.planById(s.planId)
        val cycle = s.billingCycle ?: BillingCycle.MONTHLY
        val team = s.teamId?.let(teams::findTeamById)?.takeIf { it.status == TeamStatus.ACTIVE }
        if (!s.autoRenew || card == null || plan.isFree || team == null) {
            tx.executeWithoutResult { expire(reload(s), plan, now) }
            return
        }
        // 월 자동결제이고 이 워크스페이스의 가장 높은 플랜이면 초과 인원 요금을 같이 (결제일의 멤버 수 기준)
        val excess = if (combinesOverage(s, plan, team, now)) overage.billableExcess(team, plan) else 0
        val payment = tx.execute { payments.save(newPayment(s.userId, team.id!!, team.name, plan, cycle, card, excess)) }!!
        try {
            chargeAndApply(payment.orderId, card) { at ->
                if (excess > 0 || combinesOverage(s, plan, team, now)) overage.settingsTeam(team).clearOverageSchedule()
                reload(s).also { it.extend(cycle, at) }
            }
            log.info("자동결제 성공 (subscriptionId={}, {} {}, 초과 {}명)", s.id, plan.code, cycle, excess)
        } catch (e: TossException) {
            if (e.uncertain) {
                // 결제됐는지 모름: 다음 주기에 같은 구독을 다시 시도하지 않도록 재시도 시각만 미룬다
                log.error("자동결제 결과를 모름 (orderId={}): 토스 상점관리자에서 확인 필요", payment.orderId)
            }
            tx.executeWithoutResult {
                val sub = reload(s)
                sub.paymentFailed(now)
                if (sub.failedAttempts >= Subscription.MAX_PAYMENT_ATTEMPTS) {
                    expire(sub, plan, now)
                } else {
                    notifyUser(sub, NotificationType.PAYMENT_FAILED, plan, sub.nextRetryAt, PlanMailKind.PAYMENT_FAILED, e.message)
                }
            }
        }
    }

    private fun willRenew(s: Subscription) = s.autoRenew && billingKeys.findByUserIdAndActiveTrue(s.userId) != null

    /** 이 구독 갱신에 초과 인원 요금을 같이 넣는지: 월 결제이고, 이보다 높은 플랜이 따로 쓰이고 있지 않을 때 */
    private fun combinesOverage(s: Subscription, plan: Plan, team: Team, now: LocalDateTime): Boolean {
        if ((s.billingCycle ?: BillingCycle.MONTHLY) != BillingCycle.MONTHLY || !overage.settingsTeam(team).overageAllowed) return false
        val other = planService.inUseSubscription(team.id!!, now)
        return other == null || other.first.id == s.id || other.second.sortOrder <= plan.sortOrder
    }

    private fun noticeRenewalOverage(s: Subscription, now: LocalDateTime) {
        val team = s.teamId?.let(teams::findTeamById)?.takeIf { it.status == TeamStatus.ACTIVE } ?: return
        val plan = planService.planById(s.planId)
        if (!combinesOverage(s, plan, team, now)) return
        val excess = overage.billableExcess(team, plan)
        overage.noticeCharge(team, plan, "${plan.name} (1개월)", plan.price(BillingCycle.MONTHLY), excess, s.currentPeriodEnd!!)
    }

    // ----------------------------------------------------------------- 초과 인원 요금 (따로 결제)

    /**
     * 월 자동결제 플랜이 아닌 소유자(무료·연 결제·자동결제 꺼짐)의 초과 인원 요금을 한 달마다 따로 결제 (스케줄러).
     * 소유자마다 설정을 들고 있는 팀([MemberOverageService.settingsTeam]) 하나로 한 번만 청구한다.
     * 결제일 3일 전에 안내, 결제일의 멤버 수로 계산. 초과 인원이 없으면 일정을 지운다.
     * 실패하면 하루 뒤 다시, 3번 실패하면 한도 초과 허용을 끈다 (새 초대만 막힌다).
     * @return 결제를 시도한 워크스페이스 수
     */
    fun processOverage(now: LocalDateTime = LocalDateTime.now()): Int {
        var charged = 0
        for (candidate in teams.findAllByOverageAllowedTrueAndStatus(TeamStatus.ACTIVE)) {
            runCatching {
                val due = tx.execute {
                    val team = teams.findTeamById(candidate.id!!)!!
                    // 예전에 팀마다 켜 둔 설정이 남아 있어도 소유자당 한 번만
                    if (overage.settingsTeam(team).id != team.id) return@execute null
                    val plan = planService.planOfTeam(team.id!!, now)
                    val excess = overage.billableExcess(team, plan)
                    val billing = overage.billing(team, now)
                    when {
                        billing.withRenewal || excess <= 0 -> {
                            team.clearOverageSchedule()
                            null
                        }
                        else -> {
                            // 월 자동결제가 끝나 따로 내게 된 경우 등: 오늘부터 한 달 뒤
                            team.scheduleOverage(now.plusMonths(1))
                            val at = team.overageNextChargeAt!!
                            if (at.isAfter(now)) {
                                if (at.isBefore(now.plusDays(EXPIRING_NOTICE_DAYS))) overage.noticeCharge(team, plan, null, 0, excess, at)
                                null
                            } else {
                                Triple(team.id!!, plan, excess)
                            }
                        }
                    }
                } ?: return@runCatching
                chargeOverage(due.first, due.second, due.third, now)
                charged++
            }.onFailure { log.error("초과 인원 요금 처리 실패 (teamId={})", candidate.id, it) }
        }
        return charged
    }

    private fun chargeOverage(teamId: Long, plan: Plan, excess: Long, now: LocalDateTime) {
        val team = teams.findTeamById(teamId)!!
        val card = billingKeys.findByUserIdAndActiveTrue(team.ownerId)
        if (card == null) {
            tx.executeWithoutResult { overageFailed(teamId, plan, now, "등록된 카드가 없어요") }
            return
        }
        val amount = excess * plan.overagePricePerMember
        val payment = tx.execute {
            payments.save(
                Payment(
                    userId = team.ownerId,
                    planId = plan.id!!,
                    orderId = "ORD-" + UUID.randomUUID().toString().replace("-", ""),
                    orderName = "MQ 초과 인원 ${excess}명 (1개월)".take(100),
                    amount = amount,
                    billingKeyId = card.id,
                    billingCycle = BillingCycle.MONTHLY,
                    teamId = teamId,
                    paymentKind = PaymentKind.OVERAGE,
                    overageMembers = excess.toInt(),
                    overageAmount = amount,
                ),
            )
        }!!
        val user = users.findUserById(team.ownerId)
        val result = try {
            toss.charge(cipher.decrypt(card.billingKey), card.customerKey, amount, payment.orderId, payment.orderName, user?.email, user?.realName ?: user?.nickname)
        } catch (e: TossException) {
            tx.executeWithoutResult {
                if (!e.uncertain) payments.findByOrderId(payment.orderId)!!.fail(e.code, e.message)
                overageFailed(teamId, plan, now, e.message)
            }
            log.info("초과 인원 요금 결제 실패 (teamId={}, {}명, {}원): {}", teamId, excess, amount, e.message)
            return
        }
        tx.executeWithoutResult {
            payments.findByOrderId(payment.orderId)!!.approve(
                result.paymentKey, result.method, result.receiptUrl, LocalDateTime.now(),
                cardCompany = result.cardCompany ?: card.cardCompany,
                cardNumberMasked = result.cardNumber ?: card.cardNumberMasked,
                approveNo = result.approveNo,
            )
            teams.findTeamById(teamId)!!.overageCharged()
        }
        log.info("초과 인원 요금 결제 성공 (teamId={}, {}명, {}원)", teamId, excess, amount)
    }

    private fun overageFailed(teamId: Long, plan: Plan, now: LocalDateTime, reason: String?) {
        val team = teams.findTeamById(teamId)!!
        team.overageChargeFailed(now)
        val gaveUp = team.overageFailedAttempts >= Team.MAX_OVERAGE_ATTEMPTS
        if (gaveUp) team.disallowOverage()
        val path = "$BILLING_PATH?team=$teamId"
        val label = "초과 인원 요금 (${team.name})"
        notifications.notify(
            listOf(team.ownerId),
            NotificationType.PAYMENT_FAILED,
            mapOf("planCode" to "OVERAGE", "planName" to label, "teamName" to team.name, "date" to team.overageNextChargeAt?.let(JacksonConfig::toOffsetString), "reason" to reason),
            path,
        )
        val owner = users.findUserById(team.ownerId) ?: return
        if (owner.email.isNotBlank()) {
            mail.sendAfterCommit(MailTemplates.planNotice(owner.email, MailLanguage.KO, PlanMailKind.PAYMENT_FAILED, label, team.overageNextChargeAt, reason, web.link(path)))
        }
    }

    private fun notifyExpiring(s: Subscription, now: LocalDateTime) {
        if (s.expiringNotifiedAt != null) return
        s.markExpiringNotified(now)
        notifyUser(s, NotificationType.PLAN_EXPIRING, planService.planById(s.planId), s.currentPeriodEnd, PlanMailKind.EXPIRING, null)
    }

    private fun expire(s: Subscription, plan: Plan, now: LocalDateTime) {
        s.expire(now)
        notifyUser(s, NotificationType.PLAN_EXPIRED, plan, s.currentPeriodEnd, PlanMailKind.EXPIRED, null)
    }

    // -----------------------------------------------------------------

    /** 결제 → 성공하면 같은 트랜잭션에서 결제 승인 기록 + 구독 반영. 실패하면 결제 실패 기록을 남기고 TossException 을 다시 던진다 */
    private fun chargeAndApply(orderId: String, card: BillingKey, apply: (LocalDateTime) -> Subscription) {
        val payment = payments.findByOrderId(orderId)!!
        val user = users.findUserById(payment.userId)
        val result = try {
            toss.charge(cipher.decrypt(card.billingKey), card.customerKey, payment.amount, payment.orderId, payment.orderName, user?.email, user?.realName ?: user?.nickname)
        } catch (e: TossException) {
            if (!e.uncertain) tx.executeWithoutResult { payments.findByOrderId(orderId)!!.fail(e.code, e.message) }
            throw e
        }
        tx.executeWithoutResult {
            val now = LocalDateTime.now()
            val p = payments.findByOrderId(orderId)!!
            p.approve(
                result.paymentKey, result.method, result.receiptUrl, now,
                cardCompany = result.cardCompany ?: card.cardCompany,
                cardNumberMasked = result.cardNumber ?: card.cardNumberMasked,
                approveNo = result.approveNo,
            )
            val subscription = apply(now)
            p.linkSubscription(subscription.id!!, subscription.currentPeriodEnd)
        }
    }

    private fun newPayment(userId: Long, teamId: Long, teamName: String, plan: Plan, cycle: BillingCycle, card: BillingKey, excess: Long = 0) = Payment(
        userId = userId,
        planId = plan.id!!,
        orderId = "ORD-" + UUID.randomUUID().toString().replace("-", ""),
        // 토스 주문명은 100자까지
        orderName = ("MQ ${plan.name} (${if (cycle == BillingCycle.YEARLY) "1년" else "1개월"})" +
            (if (excess > 0) " + 초과 ${excess}명" else "") + " · $teamName").take(100),
        amount = plan.price(cycle) + excess * plan.overagePricePerMember,
        billingKeyId = card.id,
        billingCycle = cycle,
        teamId = teamId,
        overageMembers = excess.toInt(),
        overageAmount = excess * plan.overagePricePerMember,
    )

    private fun reload(s: Subscription) = subscriptions.findSubscriptionById(s.id!!)!!

    /** 결제한 사람에게 알림·메일 (워크스페이스 이름과 함께) */
    private fun notifyUser(s: Subscription, type: NotificationType, plan: Plan, date: LocalDateTime?, kind: PlanMailKind, reason: String?) {
        val teamName = s.teamId?.let(teams::findTeamById)?.name
        val path = BILLING_PATH + (s.teamId?.let { "?team=$it" } ?: "")
        notifications.notify(
            listOf(s.userId),
            type,
            mapOf(
                "planCode" to plan.code, "planName" to plan.name, "teamName" to teamName,
                "date" to date?.let(JacksonConfig::toOffsetString), "reason" to reason,
            ),
            path,
        )
        val user = users.findUserById(s.userId) ?: return
        if (user.email.isBlank()) return
        val label = teamName?.let { "${plan.name} ($it)" } ?: plan.name
        mail.sendAfterCommit(MailTemplates.planNotice(user.email, MailLanguage.KO, kind, label, date, reason, web.link(path)))
    }

    private fun requireEnabled() {
        if (!tossProperties.enabled) throw BusinessException(ErrorCode.PAYMENT_NOT_CONFIGURED)
    }

    companion object {
        const val EXPIRING_NOTICE_DAYS = 3L
        const val BILLING_PATH = "/app/billing"
    }
}
