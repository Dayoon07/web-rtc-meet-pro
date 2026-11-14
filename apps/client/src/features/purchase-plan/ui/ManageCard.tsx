import { useMutation } from '@tanstack/react-query'
import { CreditCard } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { planApi, type Subscription } from '@/entities/plan'
import { toastError } from '@/shared/api'
import { Button, Modal } from '@/shared/ui'
import { cardLabel, isUserCancel, openCardRegistration } from '../lib/purchase-plan'
import { useInvalidateBilling } from '../model/use-invalidate-billing'

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
