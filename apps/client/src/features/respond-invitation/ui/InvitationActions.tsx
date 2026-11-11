import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { type Invitation, teamApi, teamKeys } from '@/entities/team'
import { toastError } from '@/shared/api'
import { Button } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'

export function InvitationActions({ invitation }: { invitation: Invitation }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: teamKeys.myInvitations })
    void queryClient.invalidateQueries({ queryKey: teamKeys.all })
  }

  const accept = useMutation({
    mutationFn: () => teamApi.accept(invitation.id),
    onSuccess: (team) => {
      refresh()
      toast.success(t('invitations.accepted', { teamName: team.name }))
      navigate(ROUTES.team(team.id))
    },
    onError: (e) => toastError(e),
  })
  const decline = useMutation({
    mutationFn: () => teamApi.decline(invitation.id),
    onSuccess: () => {
      refresh()
      toast(t('invitations.declined'))
    },
    onError: (e) => toastError(e),
  })

  return (
    <div className="flex gap-2">
      <Button variant="ghost" size="sm" loading={decline.isPending} disabled={accept.isPending} onClick={() => decline.mutate()}>
        {t('invitations.decline')}
      </Button>
      <Button size="sm" loading={accept.isPending} disabled={decline.isPending} onClick={() => accept.mutate()}>
        {t('invitations.accept')}
      </Button>
    </div>
  )
}
