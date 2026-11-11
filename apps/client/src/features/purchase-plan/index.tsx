import { loadTossPayments } from '@tosspayments/tosspayments-sdk'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CreditCard } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import {
  type BillingCycle,
  type Card,
  formatPrice,
  type Plan,
  planApi,
  planKeys,
  planName,
  planPrice,
  type Subscription,
  type SubscriptionItem,
} from '@/entities/plan'
import { useIsAuthenticated } from '@/entities/session'
import { userKeys } from '@/entities/user'
import { toastError } from '@/shared/api'
import { Button, Checkbox, Modal } from '@/shared/ui'

/** 카드 등록 뒤 이어서 결제할 내용 (토스 등록창에서 돌아오는 주소에 실어 보낸다) */
export type PurchaseIntent = { teamId: number; plan: string; cycle: BillingCycle; autoRenew: boolean }

export const CARD_RETURN_PATH = '/app/billing/card'

/**
 * 토스 카드 등록창(자동결제)을 연다. 등록이 끝나면 토스가 CARD_RETURN_PATH 로 돌려보내고,
 * 그 화면(CardReturnPage)이 카드를 등록한 뒤 intent 가 있으면 바로 결제한다.
 */
export async function openCardRegistration(intent?: PurchaseIntent) {
  const prepared = await planApi.prepareCard()
  const params = new URLSearchParams()
  if (intent) {
    params.set('team', String(intent.teamId))
    params.set('plan', intent.plan)
    params.set('cycle', intent.cycle)
    params.set('autoRenew', intent.autoRenew ? '1' : '0')
  }
  const base = window.location.origin + CARD_RETURN_PATH
  const query = params.toString()
  const toss = await loadTossPayments(prepared.clientKey)
  await toss.payment({ customerKey: prepared.customerKey }).requestBillingAuth({
    method: 'CARD',
    successUrl: query ? `${base}?${query}` : base,
    failUrl: `${base}?failed=1${query ? `&${query}` : ''}`,
    customerEmail: prepared.customerEmail,
    customerName: prepared.customerName,
  })
}

/** 토스 등록창을 닫으면 SDK 가 USER_CANCEL 로 끝낸다 → 조용히 무시 */
const isUserCancel = (e: unknown) => typeof e === 'object' && e !== null && 'code' in e && (e as { code: string }).code === 'USER_CANCEL'

function useInvalidateBilling() {
  const queryClient = useQueryClient()
  return (subscription?: Subscription) => {
    if (subscription) queryClient.setQueryData(planKeys.workspace(subscription.team.id), subscription)
    void queryClient.invalidateQueries({ queryKey: planKeys.subscriptions })
    void queryClient.invalidateQueries({ queryKey: planKeys.payments })
    void queryClient.invalidateQueries({ queryKey: userKeys.me })
    void queryClient.invalidateQueries({ queryKey: ['teams'] })
  }
}

/**
 * 요금제 카드의 버튼: 로그인 전이면 가입, 무료면 표시만, 유료면 결제 창.
 * subscription = 고른 워크스페이스의 요금제 (요금제 화면 밖에서는 없음 → 요금제 화면으로)
 */
export function PlanActionButton({
  plan,
  cycle,
  subscription,
  highlighted,
}: {
  plan: Plan
  cycle: BillingCycle
  subscription?: Subscription
  highlighted?: boolean
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const isAuthenticated = useIsAuthenticated()
  const [open, setOpen] = useState(false)

  if (!isAuthenticated) {
    return (
      <Button className="w-full" variant={highlighted ? 'primary' : 'secondary'} onClick={() => navigate('/signup')}>
        {plan.monthlyPrice === 0 ? t('plan.actions.startFree') : t('plan.actions.start')}
      </Button>
    )
  }
  if (!subscription) {
    return (
      <Button className="w-full" variant={highlighted ? 'primary' : 'secondary'} onClick={() => navigate('/app/billing')}>
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
      {open && <PurchasePlanModal plan={plan} initialCycle={cycle} subscription={subscription} open onClose={() => setOpen(false)} />}
    </>
  )
}

/** 결제 창: 월/연 선택, 자동결제 체크, 등록한 카드로 결제 (카드가 없으면 카드 등록부터) */
export function PurchasePlanModal({
  plan,
  initialCycle,
  subscription,
  open,
  onClose,
}: {
  plan: Plan
  initialCycle: BillingCycle
  subscription: Subscription
  open: boolean
  onClose: () => void
}) {
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
            <a href="/terms" target="_blank" rel="noreferrer" className="underline">
              {t('legal.terms')}
            </a>
          </li>
        </ul>
        <Checkbox checked={agree} onChange={(e) => setAgree(e.target.checked)} label={t('billing.purchase.agree', { amount: formatPrice(amount) })} />
      </div>
    </Modal>
  )
}

export const cardLabel = (card: Card) => [card.company, card.numberMasked].filter(Boolean).join(' ') || '-'

/** 결제 수단: 등록한 카드 보기 / 바꾸기 / 지우기 */
export function ManageCard({ subscription }: { subscription: Subscription }) {
  const { t } = useTranslation()
  const invalidate = useInvalidateBilling()
  const [opening, setOpening] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const remove = useMutation({
    mutationFn: planApi.removeCard,
    onSuccess: () => {
      invalidate()
      setConfirm(false)
      toast.success(t('billing.card.removed'))
    },
    onError: (e) => toastError(e),
  })
  const register = async () => {
    setOpening(true)
    try {
      await openCardRegistration()
    } catch (e) {
      if (!isUserCancel(e)) toastError(e)
    } finally {
      setOpening(false)
    }
  }
  const card = subscription.card
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex min-w-0 items-center gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300">
          <CreditCard className="size-5" />
        </span>
        <div className="min-w-0">
          <p className="truncate font-medium text-gray-900 dark:text-white">{card ? cardLabel(card) : t('billing.card.none')}</p>
          <p className="text-xs text-gray-500 dark:text-gray-400">{card ? t('billing.card.hint') : t('billing.card.noneHint')}</p>
        </div>
      </div>
      <div className="flex gap-2">
        {card && (
          <Button variant="ghost" size="sm" onClick={() => setConfirm(true)}>
            {t('billing.card.remove')}
          </Button>
        )}
        <Button variant="outline" size="sm" onClick={register} loading={opening} disabled={!subscription.paymentsEnabled}>
          {card ? t('billing.card.change') : t('billing.card.register')}
        </Button>
      </div>
      <Modal
        open={confirm}
        onClose={() => setConfirm(false)}
        title={t('billing.card.removeTitle')}
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirm(false)}>
              {t('common.cancel')}
            </Button>
            <Button variant="danger" onClick={() => remove.mutate()} loading={remove.isPending}>
              {t('billing.card.remove')}
            </Button>
          </>
        }
      >
        <p className="text-sm text-gray-600 dark:text-gray-400">{t('billing.card.removeBody')}</p>
      </Modal>
    </div>
  )
}

/** 구독 하나의 자동결제 켜기·끄기 */
export function AutoRenewSwitch({ item }: { item: SubscriptionItem }) {
  const { t } = useTranslation()
  const invalidate = useInvalidateBilling()
  const mutation = useMutation({
    mutationFn: (autoRenew: boolean) => planApi.setAutoRenew(item.id, autoRenew),
    onSuccess: (s, autoRenew) => {
      invalidate(s)
      toast.success(autoRenew ? t('billing.autoRenewOnToast') : t('billing.autoRenewOffToast'))
    },
    onError: (e) => toastError(e),
  })
  return (
    <Checkbox
      checked={item.autoRenew}
      disabled={mutation.isPending || !item.mine}
      title={item.mine ? undefined : t('billing.notMine')}
      onChange={(e) => mutation.mutate(e.target.checked)}
      label={t('billing.purchase.autoRenew')}
    />
  )
}
