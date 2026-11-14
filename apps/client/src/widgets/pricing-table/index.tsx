import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { type BillingCycle, PlanCard, type Subscription, usePlansQuery } from '@/entities/plan'
import { PlanActionButton } from '@/features/purchase-plan'
import { cn } from '@/shared/lib/cn'
import { Spinner } from '@/shared/ui'

const HIGHLIGHT = 'TEAM'

export function PricingTable({ subscription }: { subscription?: Subscription }) {
  const { t } = useTranslation()
  const [cycle, setCycle] = useState<BillingCycle>('MONTHLY')
  const { data: plans, isPending, isError } = usePlansQuery()

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
            <PlanCard card={{ plan, cycle, current: subscription?.plan.code === plan.code, highlighted }} key={plan.code} action={<PlanActionButton order={{ plan, cycle, subscription }} highlighted={highlighted} />} />
          )
        })}
      </div>
    </div>
  )
}
