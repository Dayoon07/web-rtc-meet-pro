import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import type { Subscription } from '@/entities/plan'
import type { PlanOrder } from '../model/types'
import { useIsAuthenticated } from '@/entities/session'
import { Button } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { PurchasePlanModal } from './PurchasePlanModal'

/**
 * 요금제 카드의 버튼: 로그인 전이면 가입, 무료면 표시만, 유료면 결제 창.
 * subscription = 고른 워크스페이스의 요금제 (요금제 화면 밖에서는 없음 → 요금제 화면으로)
 */
/** order = 고른 플랜 · 월/연 · 워크스페이스 요금제(로그인 전이거나 워크스페이스가 없으면 없음) */
export function PlanActionButton({ order, highlighted }: { order: PlanOrder & { subscription?: Subscription }; highlighted?: boolean }) {
  const { plan, cycle, subscription } = order
  const { t } = useTranslation()
  const navigate = useNavigate()
  const isAuthenticated = useIsAuthenticated()
  const [open, setOpen] = useState(false)

  if (!isAuthenticated) {
    return (
      <Button className="w-full" variant={highlighted ? 'primary' : 'secondary'} onClick={() => navigate(ROUTES.signup)}>
        {plan.monthlyPrice === 0 ? t('plan.actions.startFree') : t('plan.actions.start')}
      </Button>
    )
  }
  if (!subscription) {
    return (
      <Button className="w-full" variant={highlighted ? 'primary' : 'secondary'} onClick={() => navigate(ROUTES.billing)}>
        {plan.monthlyPrice === 0 ? t('plan.actions.start') : t('plan.actions.choose')}
      </Button>
    )
  }

  const inUse = subscription.plan.code === plan.code
  if (plan.monthlyPrice === 0) {
    return (
      <Button variant="outline" className="w-full" disabled>
        {inUse ? t('plan.actions.current') : t('plan.actions.basic')}
      </Button>
    )
  }
  if (!subscription.canManage) {
    return (
      <Button variant="outline" className="w-full" disabled title={t('billing.ownerOnly')}>
        {inUse ? t('plan.actions.current') : t('billing.ownerOnlyShort')}
      </Button>
    )
  }
  const owned = subscription.subscriptions.some((s) => s.plan.code === plan.code)
  const label = owned ? t('plan.actions.extend') : plan.rank > subscription.plan.rank ? t('plan.actions.upgrade') : t('plan.actions.buy')
  return (
    <>
      <Button className="w-full" variant={highlighted || inUse ? 'primary' : 'secondary'} onClick={() => setOpen(true)}>
        {label}
      </Button>
      {/* 열 때마다 새로 만들어서 요금제 화면의 월/연 선택을 그대로 가져간다 */}
      {open && <PurchasePlanModal order={{ plan, cycle, subscription }} onClose={() => setOpen(false)} />}
    </>
  )
}
