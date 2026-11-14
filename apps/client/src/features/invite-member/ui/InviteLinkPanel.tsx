import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Copy, Link2, Lock, Trash2 } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { type InviteLink, teamApi, teamKeys, useInviteLinksQuery } from '@/entities/team'
import { errorMessage, toastError } from '@/shared/api'
import { ROUTES } from '@/shared/config/routes'
import { useFormat } from '@/shared/lib/format'
import { Badge, Button, Checkbox, FieldError, IconButton, Input } from '@/shared/ui'

const inviteUrl = (link: InviteLink) => `${window.location.origin}${ROUTES.join(link.token)}`

async function copy(text: string, done: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast.success(done)
  } catch {
    toast(text)
  }
}

function LinkRow({ link, onDelete }: { link: InviteLink; onDelete: () => void }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const url = inviteUrl(link)
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-gray-200 p-3 dark:border-gray-800">
      <div className="flex items-center gap-2">
        <Link2 className="size-4 shrink-0 text-gray-400" />
        <span className="min-w-0 flex-1 truncate font-mono text-xs text-gray-700 dark:text-gray-300" title={url}>
          {url}
        </span>
        <IconButton label={t('teams.inviteLink.copy')} onClick={() => void copy(url, t('teams.inviteLink.copied'))}>
          <Copy className="size-4" />
        </IconButton>
        <IconButton label={t('teams.inviteLink.delete')} onClick={onDelete}>
          <Trash2 className="size-4 text-red-500" />
        </IconButton>
      </div>
      <div className="flex flex-wrap items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
        {link.hasPassword ? (
          <Badge tone="brand">
            <Lock className="mr-1 inline size-3" />
            {t('teams.inviteLink.withPassword')}
          </Badge>
        ) : (
          <Badge tone="gray">{t('teams.inviteLink.open')}</Badge>
        )}
        <span>{t('teams.inviteLink.joined', { count: link.joinCount })}</span>
        <span>·</span>
        <span>{t('teams.inviteLink.by', { name: link.createdByName ?? '', date: fmt.date(link.createdAt) })}</span>
      </div>
    </li>
  )
}

export function InviteLinkPanel({ teamId }: { teamId: number }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data: links = [] } = useInviteLinksQuery(teamId)
  const [usePassword, setUsePassword] = useState(false)
  const [password, setPassword] = useState('')
  const refresh = () => void queryClient.invalidateQueries({ queryKey: teamKeys.inviteLinks(teamId) })

  const create = useMutation({
    mutationFn: () => teamApi.createInviteLink(teamId, usePassword ? password : undefined),
    onSuccess: (link) => {
      setPassword('')
      setUsePassword(false)
      refresh()
      void copy(inviteUrl(link), t('teams.inviteLink.created'))
    },
  })
  const remove = useMutation({
    mutationFn: (linkId: number) => teamApi.deleteInviteLink(teamId, linkId),
    onSuccess: () => {
      toast(t('teams.inviteLink.deleted'))
      refresh()
    },
    onError: (e) => toastError(e),
  })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (usePassword && password.length < 4) return
    create.mutate()
  }

  return (
    <div className="flex flex-col gap-5">
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <p className="text-sm text-gray-500 dark:text-gray-400">{t('teams.inviteLink.hint')}</p>
        <Checkbox
          checked={usePassword}
          onChange={(e) => setUsePassword(e.target.checked)}
          label={t('teams.inviteLink.usePassword')}
          description={t('teams.inviteLink.usePasswordHint')}
        />
        {usePassword && (
          <Input
            type="password"
            autoComplete="new-password"
            aria-label={t('teams.inviteLink.password')}
            placeholder={t('teams.inviteLink.passwordPlaceholder')}
            maxLength={64}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        )}
        <Button type="submit" icon={<Link2 className="size-4" />} loading={create.isPending} disabled={usePassword && password.length < 4}>
          {t('teams.inviteLink.create')}
        </Button>
        <FieldError>{errorMessage(create.error)}</FieldError>
      </form>

      <div className="flex flex-col gap-2">
        <h4 className="text-sm font-semibold text-gray-900 dark:text-white">{t('teams.inviteLink.listTitle', { count: links.length })}</h4>
        {links.length ? (
          <ul className="flex flex-col gap-2">
            {links.map((link) => (
              <LinkRow
                key={link.id}
                link={link}
                onDelete={() => {
                  if (window.confirm(t('teams.inviteLink.deleteConfirm'))) remove.mutate(link.id)
                }}
              />
            ))}
          </ul>
        ) : (
          <p className="text-sm text-gray-400">{t('teams.inviteLink.empty')}</p>
        )}
      </div>
    </div>
  )
}
