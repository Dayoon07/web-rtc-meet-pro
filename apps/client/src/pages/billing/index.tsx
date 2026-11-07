import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Receipt } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import {
  type BillingCycle,
  formatPrice,
  type Payment,
  planApi,
  planKeys,
  planName,
  type SubscriptionItem,
  useMySubscriptionQuery,
  usePaymentsQuery,
} from '@/entities/plan'
import { userKeys } from '@/entities/user'
import { AutoRenewSwitch, ManageCard } from '@/features/purchase-plan'
import { errorMessage } from '@/shared/api'
import { useFormat } from '@/shared/lib/format'
import { Badge, Card, FullPageSpinner, Spinner } from '@/shared/ui'
import { PricingTable } from '@/widgets/pricing-table'

export function BillingPage() {
  const { t } = useTranslation()
  const fmt = useFormat()
  const { data: sub } = useMySubscriptionQuery()

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-8">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('billing.title')}</h2>
        <p className="mt-1 text-gray-500 dark:text-gray-400">{t('billing.subtitle')}</p>
      </div>

      {sub && (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
          <Card className="flex flex-col gap-4 p-6">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h3 className="font-semibold text-gray-900 dark:text-white">{t('billing.current.title')}</h3>
              <p className="text-sm text-gray-500 dark:text-gray-400">
                {sub.currentPeriodEnd
                  ? t('billing.current.until', { name: planName(sub.plan), date: fmt.date(sub.currentPeriodEnd) })
                  : t('billing.current.free')}
              </p>
            </div>
            {sub.subscriptions.length === 0 ? (
              <p className="rounded-xl bg-gray-50 p-4 text-sm text-gray-600 dark:bg-gray-800/60 dark:text-gray-400">{t('billing.current.none')}</p>
            ) : (
              <ul className="flex flex-col divide-y divide-gray-100 dark:divide-gray-800">
                {sub.subscriptions.map((s) => (
                  <SubscriptionRow key={s.id} item={s} />
                ))}
              </ul>
            )}
            <p className="text-xs text-gray-500 dark:text-gray-400">{t('billing.current.stackRule')}</p>
          </Card>

          <Card className="flex flex-col gap-4 p-6">
            <h3 className="font-semibold text-gray-900 dark:text-white">{t('billing.card.title')}</h3>
            <ManageCard subscription={sub} />
            {!sub.paymentsEnabled && <p className="text-xs font-medium text-amber-600 dark:text-amber-400">{t('billing.paymentsDisabled')}</p>}
          </Card>
        </div>
      )}

      <PricingTable />

      <PaymentHistory />
    </div>
  )
}

function SubscriptionRow({ item }: { item: SubscriptionItem }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
      <div className="min-w-0">
        <p className="flex flex-wrap items-center gap-2 font-medium text-gray-900 dark:text-white">
          {planName(item.plan)}
          {item.billingCycle && <span className="text-sm font-normal text-gray-500 dark:text-gray-400">{t(`billing.cycle.${item.billingCycle}`)}</span>}
          {item.status === 'PAST_DUE' ? (
            <Badge tone="gray">{t('billing.status.pastDue')}</Badge>
          ) : item.inUse ? (
            <Badge tone="green">{t('plan.inUse')}</Badge>
          ) : (
            <Badge tone="gray">{t('billing.status.waiting')}</Badge>
          )}
        </p>
        <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          {item.status === 'PAST_DUE' && item.nextRetryAt
            ? t('billing.retryAt', { date: fmt.dateTime(item.nextRetryAt), count: item.failedAttempts })
            : item.currentPeriodEnd &&
              (item.autoRenew
                ? t('billing.renewsAt', { date: fmt.date(item.currentPeriodEnd) })
                : t('billing.endsAt', { date: fmt.date(item.currentPeriodEnd) }))}
        </p>
      </div>
      <AutoRenewSwitch item={item} />
    </li>
  )
}

function PaymentHistory() {
  const { t } = useTranslation()
  const fmt = useFormat()
  const { data, isPending } = usePaymentsQuery()
  return (
    <Card className="p-6">
      <h3 className="mb-4 font-semibold text-gray-900 dark:text-white">{t('billing.history.title')}</h3>
      {isPending ? (
        <div className="flex justify-center py-6">
          <Spinner className="size-5 text-brand-600" />
        </div>
      ) : !data?.length ? (
        <p className="text-sm text-gray-500 dark:text-gray-400">{t('billing.history.empty')}</p>
      ) : (
        <div className="-mx-6 overflow-x-auto px-6">
          <table className="w-full min-w-[560px] text-left text-sm">
            <thead className="text-xs text-gray-500 dark:text-gray-400">
              <tr>
                <th className="pb-2 font-medium">{t('billing.history.date')}</th>
                <th className="pb-2 font-medium">{t('billing.history.item')}</th>
                <th className="pb-2 text-right font-medium">{t('billing.history.amount')}</th>
                <th className="pb-2 pl-4 font-medium">{t('billing.history.status')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {data.map((p) => (
                <tr key={p.orderId} className="align-top">
                  <td className="py-2.5 whitespace-nowrap text-gray-600 dark:text-gray-400">{fmt.dateTime(p.approvedAt ?? p.createdAt)}</td>
                  <td className="py-2.5 text-gray-900 dark:text-white">
                    {planName({ code: p.planCode, name: p.planCode })}
                    {p.billingCycle && <span className="text-gray-500 dark:text-gray-400"> · {t(`billing.cycle.${p.billingCycle}`)}</span>}
                    {p.periodEnd && <span className="block text-xs text-gray-500 dark:text-gray-400">{t('billing.history.until', { date: fmt.date(p.periodEnd) })}</span>}
                  </td>
                  <td className="py-2.5 text-right whitespace-nowrap text-gray-900 tabular-nums dark:text-white">{formatPrice(p.amount)}</td>
                  <td className="py-2.5 pl-4">
                    <PaymentStatusCell payment={p} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

function PaymentStatusCell({ payment }: { payment: Payment }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col gap-0.5">
      <span className="flex items-center gap-2">
        <Badge tone={payment.status === 'DONE' ? 'green' : 'gray'}>{t(`billing.history.statuses.${payment.status}`)}</Badge>
        {payment.receiptUrl && (
          <a href={payment.receiptUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs font-semibold text-brand-700 hover:underline dark:text-brand-300">
            <Receipt className="size-3.5" />
            {t('billing.history.receipt')}
          </a>
        )}
      </span>
      {payment.failureMessage && <span className="text-xs text-red-600 dark:text-red-400">{payment.failureMessage}</span>}
    </div>
  )
}

/**
 * 토스 카드 등록창에서 돌아오는 화면 (/app/billing/card).
 * 성공: ?customerKey=&authKey= (+ 결제할 plan, cycle, autoRenew) → 카드 등록 → 있으면 바로 결제 → 요금제 화면
 * 실패: ?failed=1&code=&message=
 */
export function CardReturnPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [params] = useSearchParams()
  const [error, setError] = useState<string | null>(null)
  const started = useRef(false)

  const run = useMutation({
    mutationFn: async () => {
      const customerKey = params.get('customerKey')
      const authKey = params.get('authKey')
      if (!customerKey || !authKey) throw new Error(t('billing.card.returnInvalid'))
      await planApi.registerCard(customerKey, authKey)
      const plan = params.get('plan')
      if (!plan) return null
      const cycle = (params.get('cycle') === 'YEARLY' ? 'YEARLY' : 'MONTHLY') as BillingCycle
      return planApi.purchase(plan, cycle, params.get('autoRenew') !== '0')
    },
    onSuccess: (subscription) => {
      if (subscription) queryClient.setQueryData(planKeys.mySubscription, subscription)
      void queryClient.invalidateQueries({ queryKey: planKeys.mySubscription })
      void queryClient.invalidateQueries({ queryKey: planKeys.payments })
      void queryClient.invalidateQueries({ queryKey: userKeys.me })
      toast.success(subscription ? t('billing.purchased', { name: planName(subscription.plan) }) : t('billing.card.registered'))
      navigate('/app/billing', { replace: true })
    },
    // 카드는 등록됐는데 결제가 실패했을 수도 있다 → 요금제 화면에서 다시 결제할 수 있게 안내
    onError: (e) => setError(errorMessage(e)),
  })

  useEffect(() => {
    if (started.current) return
    started.current = true
    if (params.get('failed')) {
      setError(params.get('message') ?? t('billing.card.failed'))
      return
    }
    run.mutate()
  }, [params, run, t])

  if (!error) return <FullPageSpinner />
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-16 text-center">
      <h2 className="text-xl font-bold text-gray-900 dark:text-white">{t('billing.card.failedTitle')}</h2>
      <p className="text-sm text-gray-600 dark:text-gray-400">{error}</p>
      <Link to="/app/billing" className="text-sm font-semibold text-brand-700 hover:underline dark:text-brand-300">
        {t('billing.backToBilling')}
      </Link>
    </div>
  )
}
