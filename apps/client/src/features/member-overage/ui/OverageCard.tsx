import { useMutation, useQueryClient } from '@tanstack/react-query'
import { UsersRound } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatPrice, planApi, planKeys, useOverageQuery } from '@/entities/plan'
import { toastError } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { useFormat } from '@/shared/lib/format'
import { Badge, Button, Card } from '@/shared/ui'

/**
 * 워크스페이스 요금제 화면의 "멤버 한도 초과" 칸.
 * 소유자가 안내(1명당 월 요금, 결제일의 멤버 수 기준, 결제 3일 전 안내)를 읽고 동의해야 켜진다 (동의 시각은 서버에 남는다).
 */
export function OverageCard({ teamId }: { teamId: number }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const queryClient = useQueryClient()
  const { data: o } = useOverageQuery(teamId)
  const [agree, setAgree] = useState(false)
  const save = useMutation({
    mutationFn: (allowed: boolean) => planApi.setOverage(teamId, allowed, allowed && agree),
    onSuccess: (next) => {
      queryClient.setQueryData(planKeys.overage(teamId), next)
      void queryClient.invalidateQueries({ queryKey: ['teams'] })
      setAgree(false)
    },
    onError: (e) => toastError(e),
  })
  if (!o) return null
  const price = formatPrice(o.pricePerMember)

  return (
    <Card className="flex flex-col gap-4 p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 font-semibold text-gray-900 dark:text-white">
          <UsersRound className="size-4.5 text-brand-600" />
          {t('billing.overage.title')}
        </h3>
        <Badge tone={o.allowed ? 'green' : 'gray'}>{t(o.allowed ? 'billing.overage.on' : 'billing.overage.off')}</Badge>
      </div>

      <div className="grid grid-cols-3 gap-3 text-center">
        {[
          [t('billing.overage.members'), `${o.members} / ${o.limit}`],
          [t('billing.overage.excess'), t('billing.overage.people', { count: o.excess })],
          [t('billing.overage.next'), o.allowed ? formatPrice(o.nextAmount) : '-'],
        ].map(([label, value]) => (
          <div key={label} className="rounded-xl bg-gray-50 px-3 py-2.5 dark:bg-gray-800/60">
            <p className="text-xs text-gray-500">{label}</p>
            <p className={cn('mt-0.5 font-semibold tabular-nums', o.excess > 0 && label === t('billing.overage.excess') ? 'text-amber-600 dark:text-amber-400' : 'text-gray-900 dark:text-white')}>
              {value}
            </p>
          </div>
        ))}
      </div>

      <p className="text-sm leading-relaxed text-gray-600 dark:text-gray-300">
        {t('billing.overage.explain', { price })}
        {o.allowed && o.nextChargeAt && o.excess > 0 && <> {t('billing.overage.nextAt', { date: fmt.date(o.nextChargeAt), amount: formatPrice(o.nextAmount) })}</>}
        {o.allowed && <> {t(o.withRenewal ? 'billing.overage.withRenewal' : 'billing.overage.separate')}</>}
      </p>
      {o.allowed && o.agreedAt && <p className="text-xs text-gray-500">{t('billing.overage.agreedAt', { date: fmt.dateTime(o.agreedAt) })}</p>}

      {o.canManage ? (
        o.allowed ? (
          <div className="flex flex-wrap items-center gap-3">
            <Button variant="outline" loading={save.isPending} disabled={o.excess > 0} onClick={() => save.mutate(false)}>
              {t('billing.overage.turnOff')}
            </Button>
            {o.excess > 0 && <span className="text-xs text-gray-500">{t('billing.overage.cannotOff', { count: o.excess })}</span>}
          </div>
        ) : (
          <div className="flex flex-col gap-3 rounded-xl border border-gray-200 p-4 dark:border-gray-800">
            <label className="flex items-start gap-2.5 text-sm text-gray-700 dark:text-gray-300">
              <input type="checkbox" className="mt-0.5 size-4 accent-brand-600" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
              <span>{t('billing.overage.consent', { price, cap: o.hardCap })}</span>
            </label>
            <div className="flex flex-wrap items-center gap-3">
              <Button loading={save.isPending} disabled={!agree || !o.hasCard} onClick={() => save.mutate(true)}>
                {t('billing.overage.turnOn')}
              </Button>
              {!o.hasCard && <span className="text-xs font-medium text-amber-600 dark:text-amber-400">{t('billing.overage.needCard')}</span>}
            </div>
          </div>
        )
      ) : (
        <p className="text-xs font-medium text-gray-600 dark:text-gray-300">{t('billing.ownerOnly')}</p>
      )}
    </Card>
  )
}
