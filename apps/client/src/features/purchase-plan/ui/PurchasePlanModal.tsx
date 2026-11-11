import { useMutation } from '@tanstack/react-query'
import { CreditCard } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { type BillingCycle, formatPrice, planApi, planName, planPrice, type Subscription } from '@/entities/plan'
import type { PlanOrder } from '../model/types'
import { toastError } from '@/shared/api'
import { Button, Checkbox, Modal } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { cardLabel, isUserCancel, openCardRegistration } from '../lib/purchase-plan'
import { useInvalidateBilling } from '../model/use-invalidate-billing'

/** 결제 창: 월/연 선택, 자동결제 체크, 등록한 카드로 결제 (카드가 없으면 카드 등록부터) */
/** 결제 창 (열 때만 그린다). order = 고른 플랜 · 처음 고른 월/연 · 워크스페이스 요금제 */
export function PurchasePlanModal({ order, onClose }: { order: PlanOrder & { subscription: Subscription }; onClose: () => void }) {
  const { plan, cycle: initialCycle, subscription } = order
  const open = true
  const { t } = useTranslation()
  const invalidate = useInvalidateBilling()
  const [cycle, setCycle] = useState<BillingCycle>(initialCycle)
  const [autoRenew, setAutoRenew] = useState(true)
  // 결제 전 확인·동의 (전자상거래법 제8조)
  const [agree, setAgree] = useState(false)
  const [opening, setOpening] = useState(false)
  const amount = planPrice(plan, cycle)
  const higher = subscription.subscriptions.find((s) => s.plan.rank > plan.rank)

  const purchase = useMutation({
    mutationFn: () => planApi.purchase(subscription.team.id, plan.code, cycle, autoRenew, agree),
    onSuccess: (s) => {
      invalidate(s)
      toast.success(t('billing.purchased', { name: planName(plan) }))
      onClose()
    },
    onError: (e) => toastError(e),
  })

  const registerAndPay = async () => {
    setOpening(true)
    try {
      await openCardRegistration({ teamId: subscription.team.id, plan: plan.code, cycle, autoRenew })
    } catch (e) {
      if (!isUserCancel(e)) toastError(e)
    } finally {
      setOpening(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t('billing.purchase.title', { name: planName(plan), workspace: subscription.team.name })}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          {subscription.card ? (
            <Button onClick={() => purchase.mutate()} loading={purchase.isPending} disabled={!subscription.paymentsEnabled || !agree}>
              {t('billing.purchase.pay', { amount: formatPrice(amount) })}
            </Button>
          ) : (
            <Button icon={<CreditCard className="size-4" />} onClick={registerAndPay} loading={opening} disabled={!subscription.paymentsEnabled || !agree}>
              {t('billing.purchase.registerAndPay')}
            </Button>
          )}
        </>
      }
    >
      <div className="flex flex-col gap-5">
        <div role="radiogroup" aria-label={t('billing.purchase.cycle')} className="grid gap-3 sm:grid-cols-2">
          {(['MONTHLY', 'YEARLY'] as const).map((c) => (
            <button
              key={c}
              type="button"
              role="radio"
              aria-checked={cycle === c}
              onClick={() => setCycle(c)}
              className={
                'flex flex-col items-start rounded-xl border p-4 text-left transition ' +
                (cycle === c
                  ? 'border-brand-500 bg-brand-50 ring-1 ring-brand-500 dark:bg-brand-500/10'
                  : 'border-gray-200 hover:border-gray-300 dark:border-gray-700 dark:hover:border-gray-600')
              }
            >
              <span className="flex w-full items-center justify-between gap-2 text-sm font-semibold text-gray-900 dark:text-white">
                {t(`billing.cycle.${c}`)}
                {c === 'YEARLY' && (
                  <span className="rounded-full bg-brand-600 px-2 py-0.5 text-[11px] font-bold text-white">{t('billing.purchase.save10')}</span>
                )}
              </span>
              <span className="mt-1 text-lg font-bold text-gray-900 tabular-nums dark:text-white">{formatPrice(planPrice(plan, c))}</span>
              <span className="text-xs text-gray-500 dark:text-gray-400">
                {c === 'YEARLY' ? t('plan.yearlyHint', { monthly: formatPrice(Math.round(plan.yearlyPrice / 12)) }) : t('billing.purchase.everyMonth')}
              </span>
            </button>
          ))}
        </div>

        <Checkbox
          checked={autoRenew}
          onChange={(e) => setAutoRenew(e.target.checked)}
          label={t('billing.purchase.autoRenew')}
          description={autoRenew ? t('billing.purchase.autoRenewOn', { cycle: t(`billing.cycle.${cycle}`) }) : t('billing.purchase.autoRenewOff')}
        />

        <div className="rounded-xl bg-gray-50 p-4 text-sm dark:bg-gray-800/60">
          {subscription.card ? (
            <p className="flex items-center gap-2 text-gray-700 dark:text-gray-300">
              <CreditCard className="size-4 shrink-0 text-gray-400" />
              {t('billing.purchase.payWith', { card: cardLabel(subscription.card) })}
            </p>
          ) : (
            <p className="text-gray-600 dark:text-gray-400">{t('billing.purchase.noCard')}</p>
          )}
          {higher && (
            <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
              {t('billing.purchase.stackNote', { higher: planName(higher.plan), name: planName(plan) })}
            </p>
          )}
          {!subscription.paymentsEnabled && <p className="mt-2 text-xs font-medium text-amber-600 dark:text-amber-400">{t('billing.paymentsDisabled')}</p>}
        </div>

        <ul className="list-disc space-y-1 pl-5 text-xs leading-relaxed text-gray-500 dark:text-gray-400">
          <li>{t('billing.purchase.noticeRenew', { amount: formatPrice(amount), cycle: t(`billing.cycle.${cycle}`) })}</li>
          <li>{t('billing.purchase.noticeCancel')}</li>
          <li>
            {t('billing.purchase.noticeRefund')}{' '}
            <a href={ROUTES.terms} target="_blank" rel="noreferrer" className="underline">
              {t('legal.terms')}
            </a>
          </li>
        </ul>
        <Checkbox checked={agree} onChange={(e) => setAgree(e.target.checked)} label={t('billing.purchase.agree', { amount: formatPrice(amount) })} />
      </div>
    </Modal>
  )
}
