export type Plan = {
  code: string
  name: string
  description: string | null
  monthlyPrice: number
  yearlyPrice: number
  maxMembersPerTeam: number
  maxGroupRoomsPerTeam: number | null
  maxMeetingParticipants: number
  recordingEnabled: boolean
  recordingStorageGb: number
  recordingRetentionDays: number
  aiNotesMinutes: number
  liveCaptionMinutes: number
  overagePricePerMember: number
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
  inUse: boolean
  failedAttempts: number
  nextRetryAt: string | null
  mine: boolean
}

export type ExpiryNotice = {
  subscriptionId: number
  plan: Plan
  expiredAt: string | null
  teamId: number | null
  teamName: string | null
  currentPlan: Plan
}

export type Card = { company: string | null; numberMasked: string | null; registeredAt: string }

export type Subscription = {
  team: { id: number; name: string }
  appliesTo: { id: number; name: string }[]
  plan: Plan
  status: SubscriptionStatus
  startedAt: string | null
  currentPeriodEnd: string | null
  memberCount: number
  subscriptions: SubscriptionItem[]
  canManage: boolean
  card: Card | null
  paymentsEnabled: boolean
}

export type CardRegistration = { clientKey: string; customerKey: string; customerEmail: string; customerName: string }

export type PaymentStatus = 'READY' | 'DONE' | 'CANCELED' | 'FAILED' | 'ABORTED'

export type Payment = {
  orderId: string
  orderName: string
  planCode: string
  planName: string
  amount: number
  supplyAmount: number
  vat: number
  status: PaymentStatus
  billingCycle: BillingCycle | null
  method: string | null
  cardCompany: string | null
  cardNumberMasked: string | null
  approveNo: string | null
  receiptUrl: string | null
  failureMessage: string | null
  approvedAt: string | null
  periodEnd: string | null
  createdAt: string
  teamId: number | null
  teamName: string | null
  kind?: 'PLAN' | 'OVERAGE'
  overageMembers?: number
  overageAmount?: number
  refundedAmount: number
  refundedAt: string | null
  refundReason: string | null
}

export type RefundQuote = {
  orderId: string
  orderName: string
  planName: string
  paidAmount: number
  approvedAt: string | null
  periodEnd: string | null
  refundable: boolean
  refundAmount: number
  rule: 'FULL_7_DAYS' | 'PRORATED' | 'NONE'
  usedDays: number
  periodDays: number
  blockedBy: string | null
  cancelsPlan: boolean
}

export type RefundRequest = { reason?: string; agree: boolean }

export type OverageInfo = {
  teamId: number
  allowed: boolean
  agreedAt: string | null
  members: number
  limit: number
  excess: number
  pricePerMember: number
  nextAmount: number
  nextChargeAt: string | null
  withRenewal: boolean
  canManage: boolean
  hasCard: boolean
  hardCap: number
}

export type PurchaseRequest = { planCode: string; cycle: BillingCycle; autoRenew: boolean; agree: boolean }
export type AutoRenewRequest = { autoRenew: boolean }
export type RegisterCardRequest = { customerKey: string; authKey: string }
export type OverageRequest = { allowed: boolean; agree: boolean }
