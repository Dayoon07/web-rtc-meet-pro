import { useTranslation } from 'react-i18next'
import { PlanCard, useMySubscriptionQuery, usePlansQuery } from '@/entities/plan'
import { useIsAuthenticated } from '@/entities/session'
import { ChangePlanButton } from '@/features/change-plan'
import { Spinner } from '@/shared/ui'

/** 가운데(두 번째로 비싼) 플랜을 "가장 인기"로 강조 — 익숙한 SaaS 요금제 배치 */
const HIGHLIGHT = 'PLAN2'

export function PricingTable() {
  const { t } = useTranslation()
  const isAuthenticated = useIsAuthenticated()
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
    <div className="grid gap-6 pt-3 sm:grid-cols-2 xl:grid-cols-4">
      {plans.map((plan) => {
        const current = subscription?.plan.code === plan.code
        const highlighted = plan.code === HIGHLIGHT
        return (
          <PlanCard
            key={plan.code}
            plan={plan}
            current={current}
            highlighted={highlighted}
            action={<ChangePlanButton plan={plan} current={current} highlighted={highlighted} />}
          />
        )
      })}
    </div>
  )
}
