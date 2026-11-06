import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { type Plan, planApi, planKeys, planName } from '@/entities/plan'
import { useIsAuthenticated } from '@/entities/session'
import { userKeys } from '@/entities/user'
import { errorMessage } from '@/shared/api'
import { Button } from '@/shared/ui'

/**
 * 플랜 선택. 0원 플랜은 바로 변경되고,
 * 유료 플랜은 토스페이먼츠 결제창 → 승인 API 흐름으로 바꿀 예정.
 */
export function ChangePlanButton({ plan, current, highlighted }: { plan: Plan; current: boolean; highlighted?: boolean }) {
  const { t } = useTranslation()
  const isAuthenticated = useIsAuthenticated()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => planApi.change(plan.code),
    onSuccess: (subscription) => {
      queryClient.setQueryData(planKeys.mySubscription, subscription)
      queryClient.invalidateQueries({ queryKey: userKeys.me })
      toast.success(t('plan.changed', { name: planName(plan) }))
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  if (current) {
    return (
      <Button variant="outline" className="w-full" disabled>
        {t('plan.actions.current')}
      </Button>
    )
  }

  const onClick = () => (isAuthenticated ? mutation.mutate() : navigate('/signup'))
  const label = !isAuthenticated ? t('plan.actions.startFree') : plan.monthlyPrice === 0 ? t('plan.actions.change') : t('plan.actions.upgrade')

  return (
    <Button className="w-full" variant={highlighted ? 'primary' : 'secondary'} onClick={onClick} loading={mutation.isPending}>
      {label}
    </Button>
  )
}
