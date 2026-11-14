import { Mail, Users } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Navigate } from 'react-router'
import { useMyInvitationsQuery, useTeamsQuery } from '@/entities/team'
import { CreateTeamForm } from '@/features/create-team'
import { InvitationActions } from '@/features/respond-invitation'
import { ROUTES } from '@/shared/config/routes'
import { useFormat } from '@/shared/lib/format'
import { Card, FullPageSpinner } from '@/shared/ui'

function PendingInvitations() {
  const { t } = useTranslation()
  const fmt = useFormat()
  const { data: invitations } = useMyInvitationsQuery()
  if (!invitations?.length) return null
  return (
    <Card className="flex flex-col gap-4 p-6">
      <div className="flex items-center gap-2">
        <Mail className="size-5 text-brand-600 dark:text-brand-300" />
        <h3 className="font-semibold text-gray-900 dark:text-white">{t('start.invitesTitle')}</h3>
      </div>
      <p className="-mt-2 text-sm text-gray-500 dark:text-gray-400">{t('start.invitesHint')}</p>
      <ul className="flex flex-col gap-3">
        {invitations.map((inv) => (
          <li key={inv.id} className="flex flex-wrap items-center gap-3 rounded-xl border border-gray-200 p-4 dark:border-gray-800">
            <span className="flex size-10 items-center justify-center rounded-lg bg-gradient-to-br from-brand-400 to-brand-600 font-bold text-white">
              {inv.teamName.slice(0, 1).toUpperCase()}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold text-gray-900 dark:text-white">{inv.teamName}</p>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                {t('invitations.from', { inviterName: inv.inviterName })} · {t('invitations.expires', { date: fmt.date(inv.expiresAt) })}
              </p>
            </div>
            <InvitationActions invitation={inv} />
          </li>
        ))}
      </ul>
    </Card>
  )
}

export function TeamStartPage() {
  const { t } = useTranslation()
  const { data: teams } = useTeamsQuery()
  if (!teams) return <FullPageSpinner />
  if (teams.length) return <Navigate to={ROUTES.chat()} replace />
  return (
    <div className="mx-auto flex max-w-xl flex-col gap-6 py-4">
      <div className="text-center">
        <span className="mx-auto flex size-14 items-center justify-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300">
          <Users className="size-7" />
        </span>
        <h2 className="mt-4 text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('start.title')}</h2>
        <p className="mt-2 text-gray-500 dark:text-gray-400">{t('start.subtitle')}</p>
      </div>
      <PendingInvitations />
      <Card className="flex flex-col gap-4 p-6">
        <h3 className="font-semibold text-gray-900 dark:text-white">{t('start.createTitle')}</h3>
        <CreateTeamForm />
      </Card>
    </div>
  )
}
