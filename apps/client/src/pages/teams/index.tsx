import { Plus, Users } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useTeamsQuery } from '@/entities/team'
import { CreateTeamModal } from '@/features/create-team'
import { Badge, Button, EmptyState, LinkButton, Spinner } from '@/shared/ui'

export function TeamsPage() {
  const { t } = useTranslation()
  const { data: teams, isPending } = useTeamsQuery()
  const [creating, setCreating] = useState(false)

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('teams.title')}</h2>
          <p className="mt-1 text-gray-500 dark:text-gray-400">{t('teams.subtitle')}</p>
        </div>
        <Button icon={<Plus className="size-4" />} onClick={() => setCreating(true)}>
          {t('teams.create.button')}
        </Button>
      </div>

      {isPending ? (
        <Spinner className="mx-auto size-6 text-brand-600" />
      ) : !teams?.length ? (
        <EmptyState
          icon={<Users />}
          title={t('teams.empty')}
          description={t('teams.emptyHint')}
          action={
            <div className="flex gap-2">
              <Button onClick={() => setCreating(true)}>{t('teams.create.button')}</Button>
              <LinkButton to="/app/invitations" variant="outline">
                {t('nav.invitations')}
              </LinkButton>
            </div>
          }
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {teams.map((team) => (
            <Link
              key={team.id}
              to={`/app/teams/${team.id}`}
              className="flex flex-col gap-4 rounded-2xl border border-gray-200 bg-white p-5 transition hover:-translate-y-0.5 hover:shadow-md dark:border-gray-800 dark:bg-gray-900"
            >
              <div className="flex items-center gap-3">
                <span className="flex size-11 items-center justify-center rounded-xl bg-gradient-to-br from-brand-400 to-brand-600 text-lg font-bold text-white">
                  {team.name.slice(0, 1).toUpperCase()}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold text-gray-900 dark:text-white">{team.name}</p>
                  <p className="text-xs text-gray-500">{t('teams.memberCount', { count: team.memberCount })}</p>
                </div>
                <Badge tone={team.myRole === 'OWNER' ? 'brand' : 'gray'}>{t(`teams.roles.${team.myRole}`)}</Badge>
              </div>
              {team.description && <p className="line-clamp-2 text-sm text-gray-600 dark:text-gray-400">{team.description}</p>}
            </Link>
          ))}
        </div>
      )}
      <CreateTeamModal open={creating} onClose={() => setCreating(false)} />
    </div>
  )
}
