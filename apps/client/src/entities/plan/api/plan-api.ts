import { useQuery } from '@tanstack/react-query'
import { API, request } from '@/shared/api'
import type {
  AutoRenewRequest,
  BillingCycle,
  Card,
  CardRegistration,
  ExpiryNotice,
  OverageInfo,
  OverageRequest,
  Payment,
  Plan,
  PurchaseRequest,
  RefundQuote,
  RefundRequest,
  RegisterCardRequest,
  Subscription,
} from '../model/types'

export const planKeys = {
  all: ['plans'] as const,
  /** 모든 워크스페이스 요금제 (invalidate 용) */
  subscriptions: ['subscriptions'] as const,
  workspace: (teamId: number) => ['subscriptions', 'team', teamId] as const,
  notices: ['subscriptions', 'notices'] as const,
  payments: ['billing', 'payments'] as const,
  overage: (teamId: number) => ['subscriptions', 'overage', teamId] as const,
}

export const planApi = {
  list: () => request<Plan[]>(API.plans.list, { auth: false }),
  /** 워크스페이스(팀)의 요금제 상태 */
  workspace: (teamId: number) => request<Subscription>(API.teams.subscription(teamId)),
  /** 워크스페이스 플랜 결제 (소유자, 등록한 카드로) */
  /** agree: 주문 내용·자동결제·환불 안내를 확인하고 동의 (서버가 확인) */
  purchase: (teamId: number, planCode: string, cycle: BillingCycle, autoRenew: boolean, agree: boolean) =>
    request<Subscription>(API.teams.purchase(teamId), { method: 'POST', json: { planCode, cycle, autoRenew, agree } satisfies PurchaseRequest }),
  setAutoRenew: (subscriptionId: number, autoRenew: boolean) =>
    request<Subscription>(API.subscriptions.item(subscriptionId), { method: 'PATCH', json: { autoRenew } satisfies AutoRenewRequest }),
  /** 내가 결제한 구독 중 확인하지 않은 만료 */
  notices: () => request<ExpiryNotice[]>(API.subscriptions.notices),
  acknowledgeExpiry: (subscriptionId: number) => request<void>(API.subscriptions.acknowledgeExpiry(subscriptionId), { method: 'POST' }),
  prepareCard: () => request<CardRegistration>(API.billing.cardPrepare, { method: 'POST' }),
  registerCard: (customerKey: string, authKey: string) => request<Card>(API.billing.card, { method: 'POST', json: { customerKey, authKey } satisfies RegisterCardRequest }),
  removeCard: () => request<void>(API.billing.card, { method: 'DELETE' }),
  payments: () => request<Payment[]>(API.billing.payments),
  /** 환불 미리 계산 (내가 결제한 것만) */
  refundQuote: (orderId: string) => request<RefundQuote>(API.billing.refund(orderId)),
  /** 환불: 약관대로 계산한 금액, 그 플랜은 바로 끝난다 */
  refund: (orderId: string, reason: string | undefined, agree: boolean) =>
    request<RefundQuote>(API.billing.refund(orderId), { method: 'POST', json: { reason, agree } satisfies RefundRequest }),
  /** 멤버 한도 초과: 인원·초과 인원·다음 결제 금액 */
  overage: (teamId: number) => request<OverageInfo>(API.teams.overage(teamId)),
  /** 한도 초과 허용 켜기(동의 필요)·끄기 (소유자) */
  setOverage: (teamId: number, allowed: boolean, agree: boolean) =>
    request<OverageInfo>(API.teams.overage(teamId), { method: 'PUT', json: { allowed, agree } satisfies OverageRequest }),
}

export const usePlansQuery = () => useQuery({ queryKey: planKeys.all, queryFn: planApi.list })

export const useWorkspaceSubscriptionQuery = (teamId: number | undefined) =>
  useQuery({ queryKey: planKeys.workspace(teamId ?? 0), queryFn: () => planApi.workspace(teamId!), enabled: !!teamId })

export const useExpiryNoticesQuery = () => useQuery({ queryKey: planKeys.notices, queryFn: planApi.notices })

export const usePaymentsQuery = () => useQuery({ queryKey: planKeys.payments, queryFn: planApi.payments })

export const useOverageQuery = (teamId: number | undefined) =>
  useQuery({ queryKey: planKeys.overage(teamId ?? 0), queryFn: () => planApi.overage(teamId!), enabled: !!teamId })
