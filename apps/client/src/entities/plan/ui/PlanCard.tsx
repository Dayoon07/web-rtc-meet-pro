import { Check, Minus } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { intlLocale, translateDynamic } from '@/shared/i18n'
import { cn } from '@/shared/lib/cn'
import { Badge } from '@/shared/ui'
import type { Plan } from '../model/types'

/** DB 의 플랜 이름(한국어) 대신 코드로 번역. 새 플랜 코드면 DB 값 그대로 */
export const planName = (plan: Pick<Plan, 'code' | 'name'>) => translateDynamic(`plan.names.${plan.code}`, undefined, plan.name)
const planDescription = (plan: Plan) => translateDynamic(`plan.descriptions.${plan.code}`, undefined, plan.description ?? '')

type Props = {
  plan: Plan
  current?: boolean
  /** "가장 인기" 강조 */
  highlighted?: boolean
  /** features/change-plan 의 버튼 등을 슬롯으로 받는다 (entities 는 features 를 import 하지 않음) */
  action?: ReactNode
}

export function PlanCard({ plan, current, highlighted, action }: Props) {
  const { t } = useTranslation()
  const price = new Intl.NumberFormat(intlLocale(), { style: 'currency', currency: 'KRW' }).format(plan.monthlyPrice)
  const features = [
    { on: true, text: t('plan.features.teams', { count: plan.maxOwnedTeams }) },
    { on: true, text: t('plan.features.members', { count: plan.maxMembersPerTeam }) },
    { on: true, text: t('plan.features.groupRooms', { count: plan.maxGroupRoomsPerTeam }) },
    { on: true, text: t('plan.features.video') },
    { on: plan.recordingEnabled, text: t('plan.features.recording') },
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
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-bold text-gray-900 dark:text-white">{planName(plan)}</h3>
        {current && <Badge tone="green">{t('plan.inUse')}</Badge>}
      </div>
      <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{planDescription(plan)}</p>

      <p className="mt-5 flex items-baseline gap-1">
        <span className="text-4xl font-extrabold tracking-tight text-gray-900 dark:text-white">{price}</span>
        <span className="text-sm text-gray-500 dark:text-gray-400">{t('plan.perMonth')}</span>
      </p>

      <div className="mt-6">{action}</div>

      <ul className="mt-6 flex flex-col gap-3 border-t border-gray-100 pt-6 text-sm dark:border-gray-800">
        {features.map((f) => (
          <li key={f.text} className={cn('flex items-center gap-2.5', f.on ? 'text-gray-700 dark:text-gray-300' : 'text-gray-400 dark:text-gray-600')}>
            {f.on ? (
              <Check className="size-4 shrink-0 text-brand-600 dark:text-brand-400" strokeWidth={3} />
            ) : (
              <Minus className="size-4 shrink-0" />
            )}
            {f.text}
          </li>
        ))}
      </ul>
    </div>
  )
}
