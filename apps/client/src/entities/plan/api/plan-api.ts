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
  subscriptions: ['subscriptions'] as const,
  workspace: (teamId: number) => ['subscriptions', 'team', teamId] as const,
  notices: ['subscriptions', 'notices'] as const,
  payments: ['billing', 'payments'] as const,
  overage: (teamId: number) => ['subscriptions', 'overage', teamId] as const,
}

export const planApi = {
  list: () => request<Plan[]>(API.plans.list, { auth: false }),
  workspace: (teamId: number) => request<Subscription>(API.teams.subscription(teamId)),
  purchase: (teamId: number, planCode: string, cycle: BillingCycle, autoRenew: boolean, agree: boolean) =>
    request<Subscription>(API.teams.purchase(teamId), { method: 'POST', json: { planCode, cycle, autoRenew, agree } satisfies PurchaseRequest }),
  setAutoRenew: (subscriptionId: number, autoRenew: boolean) =>
    request<Subscription>(API.subscriptions.item(subscriptionId), { method: 'PATCH', json: { autoRenew } satisfies AutoRenewRequest }),
  notices: () => request<ExpiryNotice[]>(API.subscriptions.notices),
  acknowledgeExpiry: (subscriptionId: number) => request<void>(API.subscriptions.acknowledgeExpiry(subscriptionId), { method: 'POST' }),
  prepareCard: () => request<CardRegistration>(API.billing.cardPrepare, { method: 'POST' }),
  registerCard: (customerKey: string, authKey: string) => request<Card>(API.billing.card, { method: 'POST', json: { customerKey, authKey } satisfies RegisterCardRequest }),
  removeCard: () => request<void>(API.billing.card, { method: 'DELETE' }),
  payments: () => request<Payment[]>(API.billing.payments),
  refundQuote: (orderId: string) => request<RefundQuote>(API.billing.refund(orderId)),
  refund: (orderId: string, reason: string | undefined, agree: boolean) =>
    request<RefundQuote>(API.billing.refund(orderId), { method: 'POST', json: { reason, agree } satisfies RefundRequest }),
  overage: (teamId: number) => request<OverageInfo>(API.teams.overage(teamId)),
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
