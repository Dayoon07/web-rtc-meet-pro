import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CalendarX } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Outlet, useLocation, useNavigate } from 'react-router'
import { type ExpiryNotice, planApi, planKeys, planName, useExpiryNoticesQuery } from '@/entities/plan'
import { useFormat } from '@/shared/lib/format'
import { Button, Modal } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'

export function PlanExpiryGate() {
  return (
    <>
      <Outlet />
      <PlanExpiryNotice />
    </>
  )
}

function PlanExpiryNotice() {
  const { t } = useTranslation()
  const fmt = useFormat()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const { data: notices } = useExpiryNoticesQuery()
  const notice = notices?.[0]

  const acknowledge = useMutation({
    mutationFn: (id: number) => planApi.acknowledgeExpiry(id),
    onSettled: () => queryClient.invalidateQueries({ queryKey: planKeys.notices }),
  })

  if (!notice || location.pathname.startsWith(ROUTES.billingCard)) return null

  const close = () => {
    queryClient.setQueryData<ExpiryNotice[]>(planKeys.notices, (cur) => cur?.filter((n) => n.subscriptionId !== notice.subscriptionId))
    acknowledge.mutate(notice.subscriptionId)
  }
  const renew = () => {
    close()
    navigate(notice.teamId ? `${ROUTES.billing}?team=${notice.teamId}` : ROUTES.billing)
  }
  const onFree = notice.currentPlan.monthlyPrice === 0
  const name = planName(notice.plan)

  return (
    <Modal
      open
      onClose={close}
      size="sm"
      title={t('billing.expired.title', { name })}
      footer={
        <>
          <Button variant="ghost" onClick={close}>
            {t('billing.expired.later')}
          </Button>
          <Button onClick={renew}>{t('billing.expired.renew')}</Button>
        </>
      }
    >
      <div className="flex gap-4">
        <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-amber-50 text-amber-600 dark:bg-amber-500/15 dark:text-amber-400">
          <CalendarX className="size-5" />
        </span>
        <div className="flex flex-col gap-2 text-sm text-gray-600 dark:text-gray-400">
          {notice.teamName && <p className="font-medium text-gray-900 dark:text-white">{notice.teamName}</p>}
          <p>
            {notice.expiredAt
              ? t('billing.expired.body', { name, date: fmt.date(notice.expiredAt) })
              : t('billing.expired.bodyNoDate', { name })}
          </p>
          <p className="font-medium text-gray-900 dark:text-white">
            {onFree ? t('billing.expired.nowFree') : t('billing.expired.nowOther', { name: planName(notice.currentPlan) })}
          </p>
          <p className="text-xs">{t('billing.expired.dataKept')}</p>
        </div>
      </div>
    </Modal>
  )
}
