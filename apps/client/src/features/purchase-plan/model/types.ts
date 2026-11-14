import type { BillingCycle, Plan } from '@/entities/plan'

export type PurchaseIntent = { teamId: number; plan: string; cycle: BillingCycle; autoRenew: boolean }

export type PlanOrder = { plan: Plan; cycle: BillingCycle }
