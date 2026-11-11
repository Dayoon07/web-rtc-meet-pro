import { useQueryClient } from '@tanstack/react-query'
import { planKeys, type Subscription } from '@/entities/plan'
import { userKeys } from '@/entities/user'

export function useInvalidateBilling() {
  const queryClient = useQueryClient()
  return (subscription?: Subscription) => {
    if (subscription) queryClient.setQueryData(planKeys.workspace(subscription.team.id), subscription)
    void queryClient.invalidateQueries({ queryKey: planKeys.subscriptions })
    void queryClient.invalidateQueries({ queryKey: planKeys.payments })
    void queryClient.invalidateQueries({ queryKey: userKeys.me })
    void queryClient.invalidateQueries({ queryKey: ['teams'] })
  }
}
