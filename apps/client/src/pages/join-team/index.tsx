import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link2Off, Lock, Users } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { teamApi, teamKeys, useInviteLinkPreviewQuery } from '@/entities/team'
import { errorMessage } from '@/shared/api'
import { ROUTES } from '@/shared/config/routes'
import { Button, Card, EmptyState, Field, FieldError, Input, LinkButton, Spinner } from '@/shared/ui'

/**
 * 팀 초대 링크로 들어온 화면 (/join/:token). 로그인 안 했으면 로그인 뒤 다시 여기로 온다.
 * 어떤 팀인지 보여 주고, 비밀번호를 걸어 둔 링크면 비밀번호를 받는다. "팀 초대 수락" → 그 팀 채팅으로
 */
export function JoinTeamPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const token = useParams().token ?? ''
  const { data: preview, isPending, isError } = useInviteLinkPreviewQuery(token)
  const [password, setPassword] = useState('')

  const join = useMutation({
    mutationFn: () => teamApi.joinByInviteLink(token, preview?.hasPassword ? password : undefined),
    onSuccess: (joined) => {
      void queryClient.invalidateQueries({ queryKey: teamKeys.all })
      toast.success(t('join.accepted', { name: joined.teamName }))
      navigate(ROUTES.chat(joined.teamId), { replace: true })
    },
  })

  if (isPending) {
    return (
      <div className="flex justify-center py-16">
        <Spinner className="size-6 text-brand-600" />
      </div>
    )
  }
  if (isError || !preview) {
    return (
      <EmptyState
        icon={<Link2Off />}
        title={t('join.notFound')}
        description={t('join.notFoundHint')}
        action={<LinkButton to={ROUTES.chat()}>{t('join.goHome')}</LinkButton>}
      />
    )
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (preview.hasPassword && !password) return
    join.mutate()
  }

  return (
    <Card className="flex flex-col gap-6 p-6 sm:p-8">
      <div className="flex flex-col items-center gap-3 text-center">
        <span className="flex size-16 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-400 to-brand-600 text-2xl font-bold text-white">
          {preview.teamName.slice(0, 1).toUpperCase()}
        </span>
        <p className="text-sm text-gray-500 dark:text-gray-400">{t('join.title')}</p>
        <h1 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{preview.teamName}</h1>
        {preview.teamDescription && <p className="text-sm text-gray-600 dark:text-gray-300">{preview.teamDescription}</p>}
        <p className="flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400">
          <Users className="size-4" />
          {t('join.members', { count: preview.memberCount })}
        </p>
      </div>

      {preview.alreadyMember ? (
        <div className="flex flex-col gap-3">
          <p className="text-center text-sm text-gray-600 dark:text-gray-300">{t('join.already')}</p>
          <Button onClick={() => navigate(ROUTES.chat(preview.teamId), { replace: true })}>{t('join.goChat')}</Button>
        </div>
      ) : (
        <form onSubmit={onSubmit} className="flex flex-col gap-4">
          {preview.hasPassword && (
            <Field label={t('join.passwordLabel')} htmlFor="join-password" hint={t('join.passwordHint')}>
              <div className="relative">
                <Lock className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-gray-400" />
                <Input
                  id="join-password"
                  type="password"
                  autoFocus
                  autoComplete="off"
                  maxLength={64}
                  className="pl-9"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
            </Field>
          )}
          <Button type="submit" size="lg" loading={join.isPending} disabled={preview.hasPassword && !password}>
            {t('join.accept')}
          </Button>
          <FieldError>{errorMessage(join.error)}</FieldError>
        </form>
      )}
    </Card>
  )
}
