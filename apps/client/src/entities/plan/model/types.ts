export type Plan = {
  code: string
  name: string
  description: string | null
  monthlyPrice: number
  /** 연 결제 금액 (월 가격 × 12 × 0.9) */
  yearlyPrice: number
  maxOwnedTeams: number
  maxMembersPerTeam: number
  /** null = 제한 없음 */
  maxGroupRoomsPerTeam: number | null
  maxMeetingParticipants: number
  recordingEnabled: boolean
  recordingStorageGb: number
  recordingRetentionDays: number
  /** 클수록 높은 플랜 */
  rank: number
}

export type SubscriptionStatus = 'ACTIVE' | 'CANCELED' | 'EXPIRED' | 'PAST_DUE'
export type BillingCycle = 'MONTHLY' | 'YEARLY'

export type SubscriptionItem = {
  id: number
  plan: Plan
  status: SubscriptionStatus
  billingCycle: BillingCycle | null
  autoRenew: boolean
  startedAt: string
  currentPeriodEnd: string | null
  /** 지금 적용 중인 구독 (여러 개면 가장 높은 플랜 하나) */
  inUse: boolean
  failedAttempts: number
  nextRetryAt: string | null
}

export type ExpiryNotice = { subscriptionId: number; plan: Plan; expiredAt: string | null }

export type Card = { company: string | null; numberMasked: string | null; registeredAt: string }

export type Subscription = {
  /** 지금 적용 중인 플랜 (유료 구독이 없으면 FREE) */
  plan: Plan
  status: SubscriptionStatus
  startedAt: string | null
  currentPeriodEnd: string | null
  ownedTeams: number
  subscriptions: SubscriptionItem[]
  expiryNotice: ExpiryNotice | null
  card: Card | null
  paymentsEnabled: boolean
}

export type CardRegistration = { clientKey: string; customerKey: string; customerEmail: string; customerName: string }

export type PaymentStatus = 'READY' | 'DONE' | 'CANCELED' | 'FAILED' | 'ABORTED'

export type Payment = {
  orderId: string
  orderName: string
  planCode: string
  amount: number
  status: PaymentStatus
  billingCycle: BillingCycle | null
  method: string | null
  receiptUrl: string | null
  failureMessage: string | null
  approvedAt: string | null
  periodEnd: string | null
  createdAt: string
}
