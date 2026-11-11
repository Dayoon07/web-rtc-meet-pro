import type { BillingCycle, Plan } from '@/entities/plan'

/** 카드 등록 뒤 이어서 결제할 내용 (토스 등록창에서 돌아오는 주소에 실어 보낸다) */
export type PurchaseIntent = { teamId: number; plan: string; cycle: BillingCycle; autoRenew: boolean }

/** 사려는 플랜과 월/연 결제 */
export type PlanOrder = { plan: Plan; cycle: BillingCycle }
