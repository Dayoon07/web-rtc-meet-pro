import { useQuery } from '@tanstack/react-query'
import { http } from '@/shared/api'
import type { Plan, Subscription } from '../model/types'

export const planKeys = {
  all: ['plans'] as const,
  mySubscription: ['subscriptions', 'me'] as const,
}

export const planApi = {
  list: () => http<Plan[]>('/plans', { auth: false }),
  mySubscription: () => http<Subscription>('/subscriptions/me'),
  change: (planCode: string) => http<Subscription>('/subscriptions/me', { method: 'PUT', json: { planCode } }),
}

export const usePlansQuery = () => useQuery({ queryKey: planKeys.all, queryFn: planApi.list })

export const useMySubscriptionQuery = (enabled = true) =>
  useQuery({ queryKey: planKeys.mySubscription, queryFn: planApi.mySubscription, enabled })
