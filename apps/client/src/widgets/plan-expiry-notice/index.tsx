import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CalendarX } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Outlet, useLocation, useNavigate } from 'react-router'
import { planApi, planKeys, planName, useMySubscriptionQuery } from '@/entities/plan'
import { useFormat } from '@/shared/lib/format'
import { Button, Modal } from '@/shared/ui'

/**
 * 앱 화면(/app) 위에 "플랜이 만료됐어요" 창을 띄운다.
 * 서버가 아직 확인하지 않은 만료(expiryNotice)를 알려 주면 보여 주고, 닫거나 결제하러 가면 다시 안 보인다.
 * 기간이 끝난 플랜의 기능(녹화·늘어난 인원 등)은 서버가 이미 막고 있다.
 */
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
  const { data: sub } = useMySubscriptionQuery()
  const notice = sub?.expiryNotice

  const acknowledge = useMutation({
    mutationFn: (id: number) => planApi.acknowledgeExpiry(id),
    onSettled: () => queryClient.invalidateQueries({ queryKey: planKeys.mySubscription }),
  })

  // 카드 등록에서 돌아오는 중에는 띄우지 않는다
  if (!sub || !notice || location.pathname.startsWith('/app/billing/card')) return null

  const close = () => {
    queryClient.setQueryData(planKeys.mySubscription, { ...sub, expiryNotice: null })
    acknowledge.mutate(notice.subscriptionId)
  }
  const renew = () => {
    close()
    navigate('/app/billing')
  }
  const onFree = sub.plan.monthlyPrice === 0

  return (
    <Modal
      open
      onClose={close}
      size="sm"
      title={t('billing.expired.title', { name: planName(notice.plan) })}
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
          <p>
            {notice.expiredAt
              ? t('billing.expired.body', { name: planName(notice.plan), date: fmt.date(notice.expiredAt) })
              : t('billing.expired.bodyNoDate', { name: planName(notice.plan) })}
          </p>
          <p className="font-medium text-gray-900 dark:text-white">
            {onFree ? t('billing.expired.nowFree') : t('billing.expired.nowOther', { name: planName(sub.plan) })}
          </p>
          <p className="text-xs">{t('billing.expired.dataKept')}</p>
        </div>
      </div>
    </Modal>
  )
}
