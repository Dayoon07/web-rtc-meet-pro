import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { type BillingCycle, PlanCard, useMySubscriptionQuery, usePlansQuery } from '@/entities/plan'
import { useIsAuthenticated } from '@/entities/session'
import { PlanActionButton } from '@/features/purchase-plan'
import { cn } from '@/shared/lib/cn'
import { Spinner } from '@/shared/ui'

/** 작은 회사·팀용 플랜을 "가장 인기"로 강조 (녹화가 들어가는 첫 플랜) */
const HIGHLIGHT = 'TEAM'

export function PricingTable() {
  const { t } = useTranslation()
  const isAuthenticated = useIsAuthenticated()
  const [cycle, setCycle] = useState<BillingCycle>('MONTHLY')
  const { data: plans, isPending, isError } = usePlansQuery()
  const { data: subscription } = useMySubscriptionQuery(isAuthenticated)

  if (isPending)
    return (
      <div className="flex justify-center py-16">
        <Spinner className="size-6 text-brand-600" />
      </div>
    )
  if (isError) return <p className="py-16 text-center text-red-500">{t('plan.loadError')}</p>

  return (
    <div className="flex flex-col gap-6">
      <div className="flex justify-center">
        <div role="radiogroup" aria-label={t('billing.purchase.cycle')} className="inline-flex rounded-full bg-gray-100 p-1 dark:bg-gray-800">
          {(['MONTHLY', 'YEARLY'] as const).map((c) => (
            <button
              key={c}
              type="button"
              role="radio"
              aria-checked={cycle === c}
              onClick={() => setCycle(c)}
              className={cn(
                'flex h-9 items-center gap-2 rounded-full px-4 text-sm font-semibold transition',
                cycle === c ? 'bg-white text-gray-900 shadow-sm dark:bg-gray-950 dark:text-white' : 'text-gray-600 hover:text-gray-900 dark:text-gray-400',
              )}
            >
              {t(`billing.cycle.${c}`)}
              {c === 'YEARLY' && <span className="rounded-full bg-brand-600 px-2 py-0.5 text-[11px] text-white">{t('billing.purchase.save10')}</span>}
            </button>
          ))}
        </div>
      </div>
      <div className="grid gap-6 pt-3 sm:grid-cols-2 xl:grid-cols-4">
        {plans.map((plan) => {
          const highlighted = plan.code === HIGHLIGHT
          return (
            <PlanCard
              key={plan.code}
              plan={plan}
              cycle={cycle}
              current={subscription?.plan.code === plan.code}
              highlighted={highlighted}
              action={<PlanActionButton plan={plan} cycle={cycle} subscription={subscription} highlighted={highlighted} />}
            />
          )
        })}
      </div>
    </div>
  )
}
