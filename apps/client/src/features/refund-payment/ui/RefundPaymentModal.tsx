import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { formatPrice, type Payment, planApi, planKeys } from '@/entities/plan'
import { userKeys } from '@/entities/user'
import { errorMessage } from '@/shared/api'
import { ROUTES } from '@/shared/config/routes'
import { translateDynamic } from '@/shared/i18n'
import { useFormat } from '@/shared/lib/format'
import type { Dialog } from '@/shared/lib/use-dialog'
import { Button, Checkbox, FieldError, Input, Modal, Spinner } from '@/shared/ui'

/**
 * 결제 환불 (약관 제8조): 서버가 계산한 환불 금액을 보여 주고, "플랜이 바로 취소돼요" 에 동의하면 환불한다.
 * 7일 안이면 전액, 그 뒤에는 이 결제 기간 중 쓴 날수만큼 빼고. 환불하면 그 플랜은 바로 끝난다
 */
export function RefundPaymentModal({ dialog }: { dialog: Dialog<Payment> }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const queryClient = useQueryClient()
  const payment = dialog.data
  const orderId = payment?.orderId ?? ''
  const [reason, setReason] = useState('')
  const [agree, setAgree] = useState(false)
  const quote = useQuery({
    queryKey: [...planKeys.payments, orderId, 'refund'],
    queryFn: () => planApi.refundQuote(orderId),
    enabled: dialog.open && !!orderId,
    staleTime: 0,
  })

  const refund = useMutation({
    mutationFn: () => planApi.refund(orderId, reason.trim() || undefined, agree),
    onSuccess: (done) => {
      toast.success(t('billing.refund.done', { amount: formatPrice(done.refundAmount) }))
      void queryClient.invalidateQueries({ queryKey: planKeys.payments })
      void queryClient.invalidateQueries({ queryKey: planKeys.subscriptions })
      void queryClient.invalidateQueries({ queryKey: userKeys.me })
      close()
    },
  })

  const close = () => {
    setReason('')
    setAgree(false)
    refund.reset()
    dialog.close()
  }

  const q = quote.data
  return (
    <Modal
      open={dialog.open}
      onClose={close}
      title={t('billing.refund.title')}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={close}>
            {t('common.cancel')}
          </Button>
          {q?.refundable && (
            <Button variant="danger" loading={refund.isPending} disabled={!agree} onClick={() => refund.mutate()}>
              {t('billing.refund.submit', { amount: formatPrice(q.refundAmount) })}
            </Button>
          )}
        </>
      }
    >
      {quote.isPending ? (
        <div className="flex justify-center py-6">
          <Spinner className="size-5 text-brand-600" />
        </div>
      ) : quote.isError || !q ? (
        <FieldError>{errorMessage(quote.error)}</FieldError>
      ) : (
        <div className="flex flex-col gap-4 text-sm">
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 rounded-xl bg-gray-50 p-4 dark:bg-gray-800/60">
            <dt className="text-gray-500 dark:text-gray-400">{t('billing.refund.item')}</dt>
            <dd className="text-right font-medium text-gray-900 dark:text-white">{q.orderName}</dd>
            {q.approvedAt && (
              <>
                <dt className="text-gray-500 dark:text-gray-400">{t('billing.refund.paidAt')}</dt>
                <dd className="text-right text-gray-900 dark:text-white">{fmt.dateTime(q.approvedAt)}</dd>
              </>
            )}
            <dt className="text-gray-500 dark:text-gray-400">{t('billing.refund.paid')}</dt>
            <dd className="text-right text-gray-900 tabular-nums dark:text-white">{formatPrice(q.paidAmount)}</dd>
            <dt className="font-semibold text-gray-900 dark:text-white">{t('billing.refund.amount')}</dt>
            <dd className="text-right text-base font-bold text-brand-700 tabular-nums dark:text-brand-300">{formatPrice(q.refundAmount)}</dd>
          </dl>

          {q.refundable ? (
            <>
              <p className="text-gray-600 dark:text-gray-300">
                {q.rule === 'FULL_7_DAYS' ? t('billing.refund.ruleFull') : t('billing.refund.ruleProrated', { used: q.usedDays, total: q.periodDays })}{' '}
                <Link to={ROUTES.terms} target="_blank" className="font-semibold text-brand-700 hover:underline dark:text-brand-300">
                  {t('billing.refund.policy')}
                </Link>
              </p>
              {q.cancelsPlan && (
                <p className="flex gap-2 rounded-xl bg-amber-50 p-3 text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
                  <TriangleAlert className="mt-0.5 size-4 shrink-0" />
                  {t('billing.refund.cancelsPlan', { plan: q.planName })}
                </p>
              )}
              <Input aria-label={t('billing.refund.reason')} placeholder={t('billing.refund.reason')} maxLength={200} value={reason} onChange={(e) => setReason(e.target.value)} />
              <Checkbox checked={agree} onChange={(e) => setAgree(e.target.checked)} label={t('billing.refund.agree')} />
              <FieldError>{errorMessage(refund.error)}</FieldError>
            </>
          ) : (
            <p className="rounded-xl bg-gray-50 p-3 text-gray-600 dark:bg-gray-800/60 dark:text-gray-300">
              {q.blockedBy ? translateDynamic(`errors.${q.blockedBy}`, undefined, t('billing.refund.notAllowed')) : t('billing.refund.notAllowed')}
            </p>
          )}
        </div>
      )}
    </Modal>
  )
}
