import { CreditCard, House, Mail, MessageSquare, Plus, Settings, Video, X } from 'lucide-react'
import { type ReactNode, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink } from 'react-router'
import { planName } from '@/entities/plan'
import { useMyInvitationsQuery, useTeamsQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { CreateTeamModal } from '@/features/create-team'
import { cn } from '@/shared/lib/cn'
import { IconButton, Logo } from '@/shared/ui'

const itemClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors [&_svg]:size-4.5',
    isActive
      ? 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-200'
      : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-white',
  )

function NavItem({ to, icon, children, end, badge }: { to: string; icon: ReactNode; children: ReactNode; end?: boolean; badge?: number }) {
  return (
    <NavLink to={to} end={end} className={itemClass}>
      {icon}
      <span className="flex-1">{children}</span>
      {!!badge && (
        <span className="min-w-5 rounded-full bg-brand-600 px-1.5 text-center text-[11px] leading-5 font-bold text-white">{badge > 99 ? '99+' : badge}</span>
      )}
    </NavLink>
  )
}

/** 내가 만든 워크스페이스 중 가장 높은 플랜 (요금제는 워크스페이스마다) */
function PlanUsage() {
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  const { data: teams } = useTeamsQuery()
  if (!me) return null
  return (
    <div className="rounded-xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-800 dark:bg-gray-900">
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="truncate font-semibold text-gray-900 dark:text-white">{t('sidebar.planName', { name: planName(me.plan) })}</span>
        <span className="shrink-0 text-xs text-gray-500">{t('sidebar.workspaces', { count: teams?.length ?? 0 })}</span>
      </div>
      <Link to="/app/billing" className="mt-3 inline-block text-xs font-semibold text-brand-700 hover:underline dark:text-brand-300">
        {t('sidebar.upgrade')}
      </Link>
    </div>
  )
}

export function AppSidebar({ open, onClose, chatUnread }: { open: boolean; onClose: () => void; chatUnread?: number }) {
  const { t } = useTranslation()
  const { data: teams } = useTeamsQuery()
  const { data: invitations } = useMyInvitationsQuery()
  const [creating, setCreating] = useState(false)

  return (
    <>
      <div
        className={cn('fixed inset-0 z-40 bg-gray-950/40 transition-opacity md:hidden', open ? 'opacity-100' : 'pointer-events-none opacity-0')}
        onClick={onClose}
        aria-hidden
      />
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-gray-200 bg-white transition-transform md:sticky md:top-0 md:h-dvh md:translate-x-0 dark:border-gray-800 dark:bg-gray-950',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex h-16 items-center justify-between px-5">
          <Logo to="/app" />
          <IconButton label={t('nav.closeMenu')} className="md:hidden" onClick={onClose}>
            <X className="size-5" />
          </IconButton>
        </div>

        <nav className="flex flex-1 flex-col gap-6 overflow-y-auto px-3 py-2" onClick={onClose}>
          <div className="flex flex-col gap-0.5">
            <NavItem to="/app" end icon={<House />}>
              {t('nav.home')}
            </NavItem>
            <NavItem to="/app/chat" icon={<MessageSquare />} badge={chatUnread}>
              {t('nav.chat')}
            </NavItem>
            <NavItem to="/app/meetings" icon={<Video />}>
              {t('nav.meetings')}
            </NavItem>
            <NavItem to="/app/invitations" icon={<Mail />} badge={invitations?.length}>
              {t('nav.invitations')}
            </NavItem>
          </div>

          <div className="flex flex-col gap-0.5">
            <div className="flex items-center justify-between px-3 pb-1">
              <NavLink to="/app/teams" end className="text-xs font-semibold tracking-wide text-gray-400 uppercase hover:text-gray-600">
                {t('sidebar.myTeams')}
              </NavLink>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation()
                  setCreating(true)
                }}
                title={t('sidebar.createTeam')}
                aria-label={t('sidebar.createTeam')}
                className="rounded p-0.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200"
              >
                <Plus className="size-4" />
              </button>
            </div>
            {teams?.length ? (
              teams.map((team) => (
                <NavLink key={team.id} to={`/app/teams/${team.id}`} className={itemClass}>
                  <span className="flex size-5 items-center justify-center rounded-md bg-gradient-to-br from-brand-400 to-brand-600 text-[11px] font-bold text-white">
                    {team.name.slice(0, 1).toUpperCase()}
                  </span>
                  <span className="truncate">{team.name}</span>
                </NavLink>
              ))
            ) : (
              <p className="px-3 py-2 text-sm text-gray-400 dark:text-gray-500">{t('sidebar.noTeams')}</p>
            )}
          </div>

          <div className="mt-auto flex flex-col gap-0.5">
            <NavItem to="/app/billing" icon={<CreditCard />}>
              {t('nav.billing')}
            </NavItem>
            <NavItem to="/app/settings" icon={<Settings />}>
              {t('nav.settings')}
            </NavItem>
          </div>
        </nav>

        <div className="p-3">
          <PlanUsage />
        </div>
      </aside>
      <CreateTeamModal open={creating} onClose={() => setCreating(false)} />
    </>
  )
}
