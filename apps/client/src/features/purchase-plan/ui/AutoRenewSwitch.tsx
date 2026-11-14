import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { planApi, type SubscriptionItem } from '@/entities/plan'
import { toastError } from '@/shared/api'
import { Checkbox } from '@/shared/ui'
import { useInvalidateBilling } from '../model/use-invalidate-billing'

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
