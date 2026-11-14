import { Check, Minus } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { intlLocale, translateDynamic } from '@/shared/i18n'
import { cn } from '@/shared/lib/cn'
import { Badge } from '@/shared/ui'
import type { BillingCycle, Plan } from '../model/types'

export const planName = (plan: Pick<Plan, 'code' | 'name'>) => translateDynamic(`plan.names.${plan.code}`, undefined, plan.name)
const planDescription = (plan: Plan) => translateDynamic(`plan.descriptions.${plan.code}`, undefined, plan.description ?? '')

export const formatPrice = (won: number) => new Intl.NumberFormat(intlLocale(), { style: 'currency', currency: 'KRW' }).format(won)

export const planPrice = (plan: Plan, cycle: BillingCycle) => (cycle === 'YEARLY' ? plan.yearlyPrice : plan.monthlyPrice)

export type PlanCardData = { plan: Plan; cycle?: BillingCycle; current?: boolean; highlighted?: boolean }

export function PlanCard({ card, action }: { card: PlanCardData; action?: ReactNode }) {
  const { plan, cycle = 'MONTHLY', current, highlighted } = card
  const { t } = useTranslation()
  const yearly = cycle === 'YEARLY' && plan.monthlyPrice > 0
  const features = [
    { on: true, text: t('plan.features.members', { count: plan.maxMembersPerTeam }) },
    { on: true, text: t('plan.features.participants', { count: plan.maxMeetingParticipants }) },
    {
      on: true,
      text: plan.maxGroupRoomsPerTeam == null ? t('plan.features.groupRoomsUnlimited') : t('plan.features.groupRooms', { count: plan.maxGroupRoomsPerTeam }),
    },
    { on: true, text: t('plan.features.video') },
    {
      on: plan.recordingEnabled,
      text: plan.recordingEnabled
        ? t('plan.features.recordingDetail', { gb: plan.recordingStorageGb, days: plan.recordingRetentionDays })
        : t('plan.features.recording'),
    },
    {
      on: plan.aiNotesMinutes > 0,
      text: plan.aiNotesMinutes > 0 ? t('plan.features.aiNotesDetail', { minutes: plan.aiNotesMinutes }) : t('plan.features.aiNotes'),
    },
    {
      on: true,
      text: t('plan.features.overage', { price: formatPrice(plan.overagePricePerMember) }),
    },
    ...(plan.liveCaptionMinutes > 0 ? [{ on: true, text: t('plan.features.captionsDetail', { minutes: plan.liveCaptionMinutes }) }] : []),
  ]

  return (
    <div
      className={cn(
        'relative flex flex-col rounded-2xl border bg-white p-6 transition-shadow dark:bg-gray-900',
        highlighted
          ? 'border-brand-500 shadow-xl shadow-brand-600/10 ring-1 ring-brand-500 dark:shadow-brand-500/10'
          : 'border-gray-200 hover:shadow-md dark:border-gray-800',
      )}
    >
      {highlighted && (
        <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-brand-600 px-3 py-1 text-xs font-bold whitespace-nowrap text-white shadow-sm">
          {t('plan.popular')}
        </span>
      )}
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-lg font-bold text-gray-900 dark:text-white">{planName(plan)}</h3>
        {current && <Badge tone="green">{t('plan.inUse')}</Badge>}
      </div>
      <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{planDescription(plan)}</p>

      <p className="mt-5 flex flex-wrap items-baseline gap-x-1">
        <span className="text-3xl font-extrabold tracking-tight text-gray-900 tabular-nums dark:text-white">
          {formatPrice(yearly ? plan.yearlyPrice : plan.monthlyPrice)}
        </span>
        <span className="text-sm text-gray-500 dark:text-gray-400">{yearly ? t('plan.perYear') : t('plan.perMonth')}</span>
      </p>
      <p className="mt-1 h-5 text-xs text-gray-500 dark:text-gray-400">
        {yearly && t('plan.yearlyHint', { monthly: formatPrice(Math.round(plan.yearlyPrice / 12)) })}
      </p>

      <div className="mt-5">{action}</div>

      <ul className="mt-6 flex flex-col gap-3 border-t border-gray-100 pt-6 text-sm dark:border-gray-800">
        {features.map((f) => (
          <li key={f.text} className={cn('flex items-start gap-2.5', f.on ? 'text-gray-700 dark:text-gray-300' : 'text-gray-400 dark:text-gray-600')}>
            {f.on ? (
              <Check className="mt-0.5 size-4 shrink-0 text-brand-600 dark:text-brand-400" strokeWidth={3} />
            ) : (
              <Minus className="mt-0.5 size-4 shrink-0" />
            )}
            {f.text}
          </li>
        ))}
      </ul>
    </div>
  )
}
