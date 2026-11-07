import { useQuery } from '@tanstack/react-query'
import { http } from '@/shared/api'
import type { BillingCycle, Card, CardRegistration, Payment, Plan, Subscription } from '../model/types'

export const planKeys = {
  all: ['plans'] as const,
  mySubscription: ['subscriptions', 'me'] as const,
  payments: ['billing', 'payments'] as const,
}

export const planApi = {
  list: () => http<Plan[]>('/plans', { auth: false }),
  mySubscription: () => http<Subscription>('/subscriptions/me'),
  /** 등록한 카드로 바로 결제 */
  purchase: (planCode: string, cycle: BillingCycle, autoRenew: boolean) =>
    http<Subscription>('/subscriptions/purchase', { method: 'POST', json: { planCode, cycle, autoRenew } }),
  setAutoRenew: (subscriptionId: number, autoRenew: boolean) =>
    http<Subscription>(`/subscriptions/${subscriptionId}`, { method: 'PATCH', json: { autoRenew } }),
  acknowledgeExpiry: (subscriptionId: number) => http<void>(`/subscriptions/${subscriptionId}/acknowledge-expiry`, { method: 'POST' }),
  prepareCard: () => http<CardRegistration>('/billing/card/prepare', { method: 'POST' }),
  registerCard: (customerKey: string, authKey: string) => http<Card>('/billing/card', { method: 'POST', json: { customerKey, authKey } }),
  removeCard: () => http<void>('/billing/card', { method: 'DELETE' }),
  payments: () => http<Payment[]>('/billing/payments'),
}

export const usePlansQuery = () => useQuery({ queryKey: planKeys.all, queryFn: planApi.list })

export const useMySubscriptionQuery = (enabled = true) =>
  useQuery({ queryKey: planKeys.mySubscription, queryFn: planApi.mySubscription, enabled })

export const usePaymentsQuery = () => useQuery({ queryKey: planKeys.payments, queryFn: planApi.payments })
