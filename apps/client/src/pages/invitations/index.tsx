import { Mail } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useMyInvitationsQuery } from '@/entities/team'
import { InvitationActions } from '@/features/respond-invitation'
import { useFormat } from '@/shared/lib/format'
import { EmptyState, Spinner } from '@/shared/ui'

export function InvitationsPage() {
  const { t } = useTranslation()
  const fmt = useFormat()
  const { data: invitations, isPending } = useMyInvitationsQuery()

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('invitations.title')}</h2>
        <p className="mt-1 text-gray-500 dark:text-gray-400">{t('invitations.subtitle')}</p>
      </div>
      {isPending ? (
        <Spinner className="mx-auto size-6 text-brand-600" />
      ) : !invitations?.length ? (
        <EmptyState icon={<Mail />} title={t('invitations.empty')} />
      ) : (
        <ul className="flex flex-col gap-3">
          {invitations.map((inv) => (
            <li key={inv.id} className="flex flex-wrap items-center gap-4 rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
              <span className="flex size-12 items-center justify-center rounded-xl bg-gradient-to-br from-brand-400 to-brand-600 text-lg font-bold text-white">
                {inv.teamName.slice(0, 1).toUpperCase()}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold text-gray-900 dark:text-white">{inv.teamName}</p>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {t('invitations.from', { inviterName: inv.inviterName })} · {t('invitations.expires', { date: fmt.date(inv.expiresAt) })}
                </p>
              </div>
              <InvitationActions invitation={inv} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
