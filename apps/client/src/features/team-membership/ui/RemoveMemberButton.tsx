import { useMutation, useQueryClient } from '@tanstack/react-query'
import { UserMinus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { teamApi, teamKeys, type TeamMember } from '@/entities/team'
import { toastError } from '@/shared/api'
import { Button } from '@/shared/ui'

/** 팀원 목록의 "내보내기" (서버가 알려 준 removable 일 때만 보인다) */
export function RemoveMemberButton({ teamId, member }: { teamId: number; member: TeamMember }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: () => teamApi.removeMember(teamId, member.userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: teamKeys.detail(teamId) })
      toast.success(t('teams.actions.removed', { name: member.nickname }))
    },
    onError: (e) => toastError(e),
  })
  if (!member.removable) return null
  return (
    <Button
      variant="ghost"
      size="sm"
      icon={<UserMinus className="size-4" />}
      loading={remove.isPending}
      title={t('teams.actions.remove')}
      onClick={() => window.confirm(t('teams.actions.removeConfirm', { name: member.nickname })) && remove.mutate()}
    >
      <span className="hidden xl:inline">{t('teams.actions.remove')}</span>
    </Button>
  )
}
