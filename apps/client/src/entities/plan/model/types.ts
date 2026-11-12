/** 요금제. 워크스페이스(팀)마다 따로 붙는다 */
export type Plan = {
  code: string
  name: string
  description: string | null
  monthlyPrice: number
  /** 연 결제 금액 (월 가격 × 12 × 0.9) */
  yearlyPrice: number
  maxMembersPerTeam: number
  /** 워크스페이스당 채널 수. null = 제한 없음 */
  maxGroupRoomsPerTeam: number | null
  maxMeetingParticipants: number
  recordingEnabled: boolean
  recordingStorageGb: number
  recordingRetentionDays: number
  /** 한 달 AI 회의록 시간(분). 0 = 없음 */
  aiNotesMinutes: number
  /** 한 달 실시간 자막·번역 시간(분). 0 = 없음 */
  liveCaptionMinutes: number
  /** 멤버 한도를 넘은 1명당 한 달 요금 (소유자가 허용했을 때) */
  overagePricePerMember: number
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
  /** 내가 결제한 구독 (자동결제를 켜고 끌 수 있다) */
  mine: boolean
}

/** 아직 확인하지 않은 만료 → "플랜이 만료됐어요" 창 */
export type ExpiryNotice = {
  subscriptionId: number
  plan: Plan
  expiredAt: string | null
  teamId: number | null
  teamName: string | null
  /** 그 워크스페이스에 지금 적용 중인 플랜 */
  currentPlan: Plan
}

export type Card = { company: string | null; numberMasked: string | null; registeredAt: string }

/** 워크스페이스 하나의 요금제 상태 (GET /teams/{id}/subscription) */
export type Subscription = {
  team: { id: number; name: string }
  /** 이 플랜이 적용되는 팀 = 소유자가 만든 모든 팀 */
  appliesTo: { id: number; name: string }[]
  /** 지금 적용 중인 플랜 (유료 구독이 없으면 FREE) */
  plan: Plan
  status: SubscriptionStatus
  startedAt: string | null
  currentPeriodEnd: string | null
  memberCount: number
  subscriptions: SubscriptionItem[]
  /** 결제할 수 있는 사람 (워크스페이스 소유자) */
  canManage: boolean
  /** 내가 등록한 카드 */
  card: Card | null
  paymentsEnabled: boolean
}

export type CardRegistration = { clientKey: string; customerKey: string; customerEmail: string; customerName: string }

export type PaymentStatus = 'READY' | 'DONE' | 'CANCELED' | 'FAILED' | 'ABORTED'

/** 결제 내역 한 줄 + 영수증 */
export type Payment = {
  orderId: string
  orderName: string
  planCode: string
  planName: string
  amount: number
  /** 공급가액 = 금액 × 10/11, 부가세 = 금액 - 공급가액 */
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
  /** PLAN = 플랜 (+ 초과 인원), OVERAGE = 초과 인원 요금만 */
  kind?: 'PLAN' | 'OVERAGE'
  overageMembers?: number
  overageAmount?: number
  /** 환불한 금액 · 시각 · 사유 (환불 안 했으면 0 · null) */
  refundedAmount: number
  refundedAt: string | null
  refundReason: string | null
}

/** 환불 미리 계산 (약관 제8조): FULL_7_DAYS = 7일 안 전액, PRORATED = 쓴 날수만큼 빼고, NONE = 못 함 */
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
  /** 못 할 때 이유 (오류 코드) */
  blockedBy: string | null
  /** 환불하면 함께 끝나는 플랜이 지금 쓰이는 중인지 */
  cancelsPlan: boolean
}

/** 환불 요청: agree = "환불하면 플랜이 바로 취소돼요" 확인 */
export type RefundRequest = { reason?: string; agree: boolean }

/** 워크스페이스 멤버 한도 초과 (소유자가 허용하면 1명당 월 요금) */
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

// ---- 서버에 보내는 값 ----
/** agree: 주문 내용·자동결제·환불 안내를 확인하고 동의 (서버가 확인) */
export type PurchaseRequest = { planCode: string; cycle: BillingCycle; autoRenew: boolean; agree: boolean }
export type AutoRenewRequest = { autoRenew: boolean }
export type RegisterCardRequest = { customerKey: string; authKey: string }
export type OverageRequest = { allowed: boolean; agree: boolean }
