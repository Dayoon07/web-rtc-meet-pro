import { useQuery } from '@tanstack/react-query'
import { http } from '@/shared/api'
import type { BillingCycle, Card, CardRegistration, ExpiryNotice, Payment, Plan, Subscription } from '../model/types'

export const planKeys = {
  all: ['plans'] as const,
  /** 모든 워크스페이스 요금제 (invalidate 용) */
  subscriptions: ['subscriptions'] as const,
  workspace: (teamId: number) => ['subscriptions', 'team', teamId] as const,
  notices: ['subscriptions', 'notices'] as const,
  payments: ['billing', 'payments'] as const,
}

export const planApi = {
  list: () => http<Plan[]>('/plans', { auth: false }),
  /** 워크스페이스(팀)의 요금제 상태 */
  workspace: (teamId: number) => http<Subscription>(`/teams/${teamId}/subscription`),
  /** 워크스페이스 플랜 결제 (소유자, 등록한 카드로) */
  purchase: (teamId: number, planCode: string, cycle: BillingCycle, autoRenew: boolean) =>
    http<Subscription>(`/teams/${teamId}/subscription/purchase`, { method: 'POST', json: { planCode, cycle, autoRenew } }),
  setAutoRenew: (subscriptionId: number, autoRenew: boolean) =>
    http<Subscription>(`/subscriptions/${subscriptionId}`, { method: 'PATCH', json: { autoRenew } }),
  /** 내가 결제한 구독 중 확인하지 않은 만료 */
  notices: () => http<ExpiryNotice[]>('/subscriptions/notices'),
  acknowledgeExpiry: (subscriptionId: number) => http<void>(`/subscriptions/${subscriptionId}/acknowledge-expiry`, { method: 'POST' }),
  prepareCard: () => http<CardRegistration>('/billing/card/prepare', { method: 'POST' }),
  registerCard: (customerKey: string, authKey: string) => http<Card>('/billing/card', { method: 'POST', json: { customerKey, authKey } }),
  removeCard: () => http<void>('/billing/card', { method: 'DELETE' }),
  payments: () => http<Payment[]>('/billing/payments'),
}

export const usePlansQuery = () => useQuery({ queryKey: planKeys.all, queryFn: planApi.list })

export const useWorkspaceSubscriptionQuery = (teamId: number | undefined) =>
  useQuery({ queryKey: planKeys.workspace(teamId ?? 0), queryFn: () => planApi.workspace(teamId!), enabled: !!teamId })

export const useExpiryNoticesQuery = () => useQuery({ queryKey: planKeys.notices, queryFn: planApi.notices })

export const usePaymentsQuery = () => useQuery({ queryKey: planKeys.payments, queryFn: planApi.payments })
