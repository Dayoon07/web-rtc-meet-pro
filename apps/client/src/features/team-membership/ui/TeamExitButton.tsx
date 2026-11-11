import { useMutation, useQueryClient } from '@tanstack/react-query'
import { LogOut, Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { hasTeamPermission, teamApi, teamKeys, type TeamSummary } from '@/entities/team'
import { toastError } from '@/shared/api'
import { Button } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { DeleteTeamModal } from './DeleteTeamModal'
import { useDialog } from '@/shared/lib/use-dialog'

/** 팀 상세 머리의 버튼: 삭제 권한(OWNER)이 있으면 "팀 삭제", 아니면 "팀 나가기" */
export function TeamExitButton({ team }: { team: TeamSummary }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const done = (message: string) => {
    void queryClient.invalidateQueries({ queryKey: teamKeys.all })
    queryClient.removeQueries({ queryKey: teamKeys.detail(team.id) })
    toast.success(message)
    navigate(ROUTES.teams, { replace: true })
  }
  const deleting = useDialog(() => done(t('teams.actions.deleted', { name: team.name })))
  const leave = useMutation({
    mutationFn: () => teamApi.leave(team.id),
    onSuccess: () => done(t('teams.actions.left', { name: team.name })),
    onError: (e) => toastError(e),
  })

  if (hasTeamPermission(team, 'DELETE_TEAM')) {
    return (
      <>
        <Button variant="ghost" icon={<Trash2 className="size-4" />} onClick={() => deleting.show()}>
          <span className="hidden lg:inline">{t('teams.actions.delete')}</span>
        </Button>
        <DeleteTeamModal team={team} dialog={deleting} />
      </>
    )
  }
  return (
    <Button
      variant="ghost"
      icon={<LogOut className="size-4" />}
      loading={leave.isPending}
      onClick={() => window.confirm(t('teams.actions.leaveConfirm', { name: team.name })) && leave.mutate()}
    >
      <span className="hidden lg:inline">{t('teams.actions.leave')}</span>
    </Button>
  )
}
