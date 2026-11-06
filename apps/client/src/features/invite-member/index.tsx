import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Mail, Send, X } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { canInvite, teamApi, teamKeys, type TeamDetail, useTeamInvitationsQuery } from '@/entities/team'
import { errorMessage } from '@/shared/api'
import { useFormat } from '@/shared/lib/format'
import { Button, FieldError, Input, Progress } from '@/shared/ui'

/** 이메일로 팀원 초대 + 대기 중인 초대 목록 */
export function InviteMemberPanel({ detail }: { detail: TeamDetail }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const queryClient = useQueryClient()
  const teamId = detail.team.id
  const allowed = canInvite(detail.team.myRole)
  const { data: pending } = useTeamInvitationsQuery(teamId, allowed)
  const [email, setEmail] = useState('')

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: teamKeys.invitations(teamId) })
    void queryClient.invalidateQueries({ queryKey: teamKeys.detail(teamId) })
  }

  const invite = useMutation({
    mutationFn: () => teamApi.invite(teamId, email.trim()),
    onSuccess: (inv) => {
      toast.success(t('teams.members.invite.sent', { email: inv.inviteeEmail }))
      setEmail('')
      refresh()
    },
  })
  const cancel = useMutation({
    mutationFn: (invitationId: number) => teamApi.cancelInvitation(teamId, invitationId),
    onSuccess: () => {
      toast(t('teams.members.pending.canceled'))
      refresh()
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const used = detail.members.length + detail.limits.pendingInvitations

  if (!allowed) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">{t('teams.members.invite.noPermission')}</p>
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (email.trim()) invite.mutate()
  }

  return (
    <div className="flex flex-col gap-5">
      <form onSubmit={onSubmit} className="flex flex-col gap-2">
        <div className="flex gap-2">
          <Input
            type="email"
            aria-label={t('teams.members.invite.email')}
            placeholder={t('teams.members.invite.placeholder')}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Button type="submit" icon={<Send className="size-4" />} loading={invite.isPending} disabled={!email.trim()}>
            <span className="hidden sm:inline">{t('teams.members.invite.submit')}</span>
          </Button>
        </div>
        <FieldError>{errorMessage(invite.error)}</FieldError>
        <p className="text-xs text-gray-500 dark:text-gray-400">{t('teams.members.invite.hint')}</p>
      </form>

      <div className="flex flex-col gap-2">
        <p className="text-xs text-gray-500 dark:text-gray-400">
          {t('teams.members.invite.usage', { current: detail.members.length, max: detail.limits.maxMembers, pending: detail.limits.pendingInvitations })}
        </p>
        <Progress value={used} max={detail.limits.maxMembers} />
      </div>

      <div>
        <p className="mb-2 text-sm font-semibold text-gray-900 dark:text-white">{t('teams.members.pending.title')}</p>
        {!pending?.length ? (
          <p className="text-sm text-gray-400">{t('teams.members.pending.empty')}</p>
        ) : (
          <ul className="divide-y divide-gray-100 rounded-xl border border-gray-200 dark:divide-gray-800 dark:border-gray-800">
            {pending.map((inv) => (
              <li key={inv.id} className="flex items-center gap-3 px-4 py-3">
                <Mail className="size-4 shrink-0 text-gray-400" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm text-gray-800 dark:text-gray-200">{inv.inviteeEmail}</p>
                  <p className="text-xs text-gray-400">{t('teams.members.pending.expires', { date: fmt.date(inv.expiresAt) })}</p>
                </div>
                <button
                  type="button"
                  onClick={() => cancel.mutate(inv.id)}
                  title={t('teams.members.pending.cancel')}
                  aria-label={t('teams.members.pending.cancel')}
                  className="rounded-md p-1.5 text-gray-400 hover:bg-gray-100 hover:text-red-500 dark:hover:bg-gray-800"
                >
                  <X className="size-4" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
