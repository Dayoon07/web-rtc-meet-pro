import { useMutation, useQueryClient } from '@tanstack/react-query'
import { LogOut, Trash2, UserMinus } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { hasTeamPermission, teamApi, teamKeys, type TeamMember, type TeamSummary } from '@/entities/team'
import { errorMessage, toastError } from '@/shared/api'
import { Button, Field, FieldError, Input, Modal } from '@/shared/ui'

/** 팀 상세 머리의 버튼: 삭제 권한(OWNER)이 있으면 "팀 삭제", 아니면 "팀 나가기" */
export function TeamExitButton({ team }: { team: TeamSummary }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [deleting, setDeleting] = useState(false)

  const done = (message: string) => {
    void queryClient.invalidateQueries({ queryKey: teamKeys.all })
    queryClient.removeQueries({ queryKey: teamKeys.detail(team.id) })
    toast.success(message)
    navigate('/app/teams', { replace: true })
  }

  const leave = useMutation({
    mutationFn: () => teamApi.leave(team.id),
    onSuccess: () => done(t('teams.actions.left', { name: team.name })),
    onError: (e) => toastError(e),
  })

  if (hasTeamPermission(team, 'DELETE_TEAM')) {
    return (
      <>
        <Button variant="ghost" icon={<Trash2 className="size-4" />} onClick={() => setDeleting(true)}>
          <span className="hidden lg:inline">{t('teams.actions.delete')}</span>
        </Button>
        <DeleteTeamModal team={team} open={deleting} onClose={() => setDeleting(false)} onDeleted={() => done(t('teams.actions.deleted', { name: team.name }))} />
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

/** 팀 삭제는 되돌릴 수 없어서 팀 이름을 그대로 입력해야 지울 수 있다 */
function DeleteTeamModal({ team, open, onClose, onDeleted }: { team: TeamSummary; open: boolean; onClose: () => void; onDeleted: () => void }) {
  const { t } = useTranslation()
  const [typed, setTyped] = useState('')
  const remove = useMutation({ mutationFn: () => teamApi.remove(team.id), onSuccess: onDeleted })
  const close = () => {
    setTyped('')
    remove.reset()
    onClose()
  }
  return (
    <Modal
      open={open}
      onClose={close}
      title={t('teams.actions.deleteTitle')}
      size="sm"
      footer={
        <>
          <Button variant="outline" onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button variant="danger" loading={remove.isPending} disabled={typed.trim() !== team.name} onClick={() => remove.mutate()}>
            {t('teams.actions.delete')}
          </Button>
        </>
      }
    >
      <p className="text-sm text-gray-600 dark:text-gray-300">{t('teams.actions.deleteBody')}</p>
      <Field label={t('teams.actions.deleteConfirmLabel', { name: team.name })} className="mt-4">
        <Input value={typed} onChange={(e) => setTyped(e.target.value)} placeholder={team.name} autoComplete="off" />
      </Field>
      <div className="mt-2">
        <FieldError>{errorMessage(remove.error)}</FieldError>
      </div>
    </Modal>
  )
}

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
