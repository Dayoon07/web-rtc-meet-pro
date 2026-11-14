import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Building2, Receipt, Undo2 } from 'lucide-react'
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
  ReceiptModal,
  type SubscriptionItem,
  usePaymentsQuery,
  useWorkspaceSubscriptionQuery,
} from '@/entities/plan'
import { useTeamsQuery } from '@/entities/team'
import { userKeys } from '@/entities/user'
import { OverageCard } from '@/features/member-overage'
import { AutoRenewSwitch, ManageCard } from '@/features/purchase-plan'
import { RefundPaymentModal } from '@/features/refund-payment'
import { errorMessage } from '@/shared/api'
import { useFormat } from '@/shared/lib/format'
import { Badge, Card, EmptyState, FullPageSpinner, LinkButton, Select, Spinner } from '@/shared/ui'
import { PricingTable } from '@/widgets/pricing-table'
import { ROUTES } from '@/shared/config/routes'
import { useDialog } from '@/shared/lib/use-dialog'

export function BillingPage() {
  const { t } = useTranslation()
  const fmt = useFormat()
  const [params, setParams] = useSearchParams()
  const { data: teams, isPending: teamsPending } = useTeamsQuery()
  const fromUrl = Number(params.get('team')) || undefined
  const owned = teams?.filter((tm) => tm.myRole === 'OWNER') ?? []
  const picked = teams?.some((tm) => tm.id === fromUrl) ? fromUrl : (owned[0] ?? teams?.[0])?.id
  const teamId = owned.some((tm) => tm.id === picked) ? owned[0].id : picked
  const others = teams?.filter((tm) => tm.myRole !== 'OWNER') ?? []
  const { data: sub } = useWorkspaceSubscriptionQuery(teamId)

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0 basis-64 grow">
          <h2 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('billing.title')}</h2>
          <p className="mt-1 text-gray-500 dark:text-gray-400">{t('billing.subtitle')}</p>
        </div>
        {owned.length + others.length > 1 && (
          <label className="flex w-full items-center gap-2 sm:w-72">
            <Building2 className="size-5 shrink-0 text-gray-400" />
            <span className="sr-only">{t('billing.workspace')}</span>
            <Select value={teamId ?? ''} onChange={(e) => setParams({ team: e.target.value }, { replace: true })}>
              {owned.length > 0 && <option value={owned[0].id}>{t('billing.myPlan', { count: owned.length })}</option>}
              {others.map((tm) => (
                <option key={tm.id} value={tm.id}>
                  {tm.name} ({t('billing.memberView')})
                </option>
              ))}
            </Select>
          </label>
        )}
      </div>

      {!teamsPending && !teams?.length && (
        <EmptyState
          icon={<Building2 />}
          title={t('billing.noWorkspace')}
          description={t('billing.noWorkspaceHint')}
          action={<LinkButton to={ROUTES.teams}>{t('billing.createWorkspace')}</LinkButton>}
        />
      )}

      {sub && (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
          <Card className="flex flex-col gap-4 p-6">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <div className="min-w-0">
                <h3 className="font-semibold text-gray-900 dark:text-white">
                  {sub.canManage ? t('billing.current.mine') : t('billing.current.title', { workspace: sub.team.name })}
                </h3>
                <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                  {t('billing.current.appliesTo', { teams: sub.appliesTo.map((tm) => tm.name).join(', ') })}
                </p>
              </div>
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
            {!sub.canManage && <p className="text-xs font-medium text-gray-600 dark:text-gray-300">{t('billing.ownerOnly')}</p>}
          </Card>

          <Card className="flex flex-col gap-4 p-6">
            <h3 className="font-semibold text-gray-900 dark:text-white">{t('billing.card.title')}</h3>
            <ManageCard subscription={sub} />
            {!sub.paymentsEnabled && <p className="text-xs font-medium text-amber-600 dark:text-amber-400">{t('billing.paymentsDisabled')}</p>}
          </Card>
        </div>
      )}

      {sub && <OverageCard teamId={sub.team.id} />}

      {sub && <PricingTable subscription={sub} />}

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
  const receipt = useDialog<Payment>()
  const refund = useDialog<Payment>()
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
                <th className="pb-2 pl-4 font-medium">
                  <span className="sr-only">{t('billing.receipt.title')}</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {data.map((p) => (
                <tr key={p.orderId} className="align-top">
                  <td className="py-2.5 whitespace-nowrap text-gray-600 dark:text-gray-400">{fmt.dateTime(p.approvedAt ?? p.createdAt)}</td>
                  <td className="py-2.5 text-gray-900 dark:text-white">
                    {planName({ code: p.planCode, name: p.planName })}
                    {p.billingCycle && <span className="text-gray-500 dark:text-gray-400"> · {t(`billing.cycle.${p.billingCycle}`)}</span>}
                    {(p.teamName || p.periodEnd) && (
                      <span className="block text-xs text-gray-500 dark:text-gray-400">
                        {[p.teamName, p.periodEnd && t('billing.history.until', { date: fmt.date(p.periodEnd) })].filter(Boolean).join(' · ')}
                      </span>
                    )}
                  </td>
                  <td className="py-2.5 text-right whitespace-nowrap text-gray-900 tabular-nums dark:text-white">{formatPrice(p.amount)}</td>
                  <td className="py-2.5 pl-4">
                    <PaymentStatusCell payment={p} />
                  </td>
                  <td className="py-2.5 pl-4 text-right whitespace-nowrap">
                    {p.status === 'DONE' && p.kind !== 'OVERAGE' && (
                      <button
                        type="button"
                        onClick={() => refund.show(p)}
                        className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-semibold whitespace-nowrap text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
                      >
                        <Undo2 className="size-3.5" />
                        {t('billing.refund.open')}
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => receipt.show(p)}
                      className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-semibold whitespace-nowrap text-brand-700 hover:bg-brand-50 dark:text-brand-300 dark:hover:bg-brand-500/15"
                    >
                      <Receipt className="size-3.5" />
                      {t('billing.receipt.open')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <ReceiptModal dialog={receipt} />
      <RefundPaymentModal dialog={refund} />
    </Card>
  )
}

function PaymentStatusCell({ payment }: { payment: Payment }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col gap-0.5">
      <span className="flex items-center gap-2">
        <Badge tone={payment.status === 'DONE' ? 'green' : 'gray'}>{t(`billing.history.statuses.${payment.status}`)}</Badge>
      </span>
      {payment.failureMessage && <span className="text-xs text-red-600 dark:text-red-400">{payment.failureMessage}</span>}
      {payment.refundedAmount > 0 && (
        <span className="text-xs whitespace-nowrap text-gray-500 dark:text-gray-400">{t('billing.refund.refunded', { amount: formatPrice(payment.refundedAmount) })}</span>
      )}
    </div>
  )
}

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
      const team = Number(params.get('team'))
      if (!plan || !team) return null
      const cycle = (params.get('cycle') === 'YEARLY' ? 'YEARLY' : 'MONTHLY') as BillingCycle
      return planApi.purchase(team, plan, cycle, params.get('autoRenew') !== '0', true)
    },
    onSuccess: (subscription) => {
      if (subscription) queryClient.setQueryData(planKeys.workspace(subscription.team.id), subscription)
      void queryClient.invalidateQueries({ queryKey: planKeys.subscriptions })
      void queryClient.invalidateQueries({ queryKey: planKeys.payments })
      void queryClient.invalidateQueries({ queryKey: userKeys.me })
      toast.success(subscription ? t('billing.purchased', { name: planName(subscription.plan) }) : t('billing.card.registered'))
      navigate(subscription ? `${ROUTES.billing}?team=${subscription.team.id}` : ROUTES.billing, { replace: true })
    },
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
      <Link to={ROUTES.billing} className="text-sm font-semibold text-brand-700 hover:underline dark:text-brand-300">
        {t('billing.backToBilling')}
      </Link>
    </div>
  )
}
