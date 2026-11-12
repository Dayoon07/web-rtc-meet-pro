import { ChevronLeft, ChevronRight, CreditCard, House, Mail, Settings, Video, X } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, matchPath, NavLink, useLocation } from 'react-router'
import type { ChatUnread } from '@/entities/chat'
import { planName } from '@/entities/plan'
import { useCurrentTeamId, useMyInvitationsQuery, useTeamsQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { cn } from '@/shared/lib/cn'
import { IconButton, Logo, MqMark } from '@/shared/ui'
import { APP_CHILD_ROUTES, ROUTES } from '@/shared/config/routes'
import { TeamSwitcher } from './ui/TeamSwitcher'

const itemClass = ({ isActive }: { isActive: boolean }, mini = false) =>
  cn(
    'relative flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors [&_svg]:size-4.5',
    // 접으면 (데스크톱만) 아이콘만 가운데
    mini && 'md:justify-center md:px-0',
    isActive
      ? 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-200'
      : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-white',
  )

/** 메뉴 오른쪽 숫자 (접힌 사이드바에서는 아이콘 오른쪽 위에 작게) */
function CountBadge({ count, mini }: { count?: number; mini?: boolean }) {
  if (!count) return null
  return (
    <span
      className={cn(
        'min-w-5 shrink-0 rounded-full bg-brand-600 px-1.5 text-center text-[11px] leading-5 font-bold text-white',
        mini && 'md:absolute md:top-0.5 md:right-1.5 md:min-w-4 md:px-1 md:text-[10px] md:leading-4',
      )}
    >
      {count > 99 ? '99+' : count}
    </span>
  )
}

/** item = 주소 · 아이콘 · 정확히 같은 주소일 때만 · 숫자 배지 · 접힌 사이드바, children = 이름 */
function NavItem({ item, children }: { item: { to: string; icon: ReactNode; end?: boolean; badge?: number; mini?: boolean }; children: ReactNode }) {
  const { to, icon, end, badge, mini } = item
  return (
    <NavLink to={to} end={end} className={(s) => itemClass(s, mini)} title={mini && typeof children === 'string' ? children : undefined}>
      {icon}
      <span className={cn('flex-1', mini && 'md:sr-only')}>{children}</span>
      <CountBadge count={badge} mini={mini} />
    </NavLink>
  )
}

/** 주소의 팀 (/app/chat/:teamId/... 또는 /app/teams/:teamId). 없으면 undefined → 마지막에 본 팀 */
function useAddressTeamId(): number | undefined {
  const { pathname } = useLocation()
  const match = matchPath(`${ROUTES.app}/${APP_CHILD_ROUTES.chat}`, pathname) ?? matchPath(`${ROUTES.app}/${APP_CHILD_ROUTES.team}`, pathname)
  return match?.params.teamId ? Number(match.params.teamId) : undefined
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
      <Link to={ROUTES.billing} className="mt-3 inline-block text-xs font-semibold text-brand-700 hover:underline dark:text-brand-300">
        {t('sidebar.upgrade')}
      </Link>
    </div>
  )
}

/**
 * 앱 왼쪽 메뉴. 데스크톱에서는 접을 수 있다 (collapsed: 아이콘만, 마우스를 올리면 이름).
 * 휴대폰에서는 늘 펼친 서랍 (open 으로 열고 닫음).
 */
/** 앱 사이드바의 상태: 휴대폰에서 열림 · 닫기 · 채팅 안 읽은 수 · 접힘 · 접기/펴기 */
export type AppSidebarState = {
  open: boolean
  onClose: () => void
  chatUnread?: ChatUnread
  collapsed?: boolean
  onToggleCollapsed?: () => void
  /** 바깥에서 끼워 넣는 내용: teamPanel = 지금 팀 아래 방 목록, footer = 아래쪽 (음성 막대) */
  slots?: { teamPanel?: ReactNode; footer?: ReactNode }
}

export function AppSidebar({ state }: { state: AppSidebarState }) {
  const { open, onClose, chatUnread, collapsed = false, onToggleCollapsed, slots } = state
  const mini = collapsed
  const { t } = useTranslation()
  const { data: teams } = useTeamsQuery()
  const { data: invitations } = useMyInvitationsQuery()
  const currentTeamId = useCurrentTeamId(useAddressTeamId())

  return (
    <>
      <div
        className={cn('fixed inset-0 z-40 bg-gray-950/40 transition-opacity md:hidden', open ? 'opacity-100' : 'pointer-events-none opacity-0')}
        onClick={onClose}
        aria-hidden
      />
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-gray-200 bg-white transition-[transform,width] duration-200 md:sticky md:top-0 md:h-dvh md:translate-x-0 dark:border-gray-800 dark:bg-gray-950',
          open ? 'translate-x-0' : '-translate-x-full',
          mini && 'md:w-[72px]',
        )}
      >
        <div className={cn('flex h-16 items-center justify-between px-5', mini && 'md:justify-center md:px-0')}>
          <Logo to={ROUTES.chat()} className={cn(mini && 'md:hidden')} />
          {mini && (
            <NavLink to={ROUTES.chat()} className="hidden md:flex" aria-label={t('nav.logoHome')}>
              <MqMark className="h-7 w-8" />
            </NavLink>
          )}
          <IconButton label={t('nav.closeMenu')} className="md:hidden" onClick={onClose}>
            <X className="size-5" />
          </IconButton>
          {onToggleCollapsed && !mini && (
            <IconButton label={t('nav.collapse')} title={t('nav.collapse')} className="hidden md:inline-flex" onClick={onToggleCollapsed}>
              <ChevronLeft className="size-5" />
            </IconButton>
          )}
        </div>
        {onToggleCollapsed && mini && (
          <div className="hidden justify-center pb-2 md:flex">
            <IconButton label={t('nav.expand')} title={t('nav.expand')} onClick={onToggleCollapsed}>
              <ChevronRight className="size-5" />
            </IconButton>
          </div>
        )}

        <nav className={cn('flex flex-1 flex-col gap-6 overflow-y-auto px-3 py-2', mini && 'md:px-2')} onClick={onClose}>
          <div className="flex flex-col gap-0.5">
            <NavItem item={{ to: ROUTES.app, end: true, icon: <House />, mini }}>
              {t('nav.home')}
            </NavItem>
            <NavItem item={{ to: ROUTES.meetings, icon: <Video />, mini }}>
              {t('nav.meetings')}
            </NavItem>
            <NavItem item={{ to: ROUTES.invitations, icon: <Mail />, badge: invitations?.length, mini }}>
              {t('nav.invitations')}
            </NavItem>
          </div>

          <div className="flex flex-col gap-1">
            {mini && <div className="mx-3 mb-1 hidden border-t border-gray-200 md:block dark:border-gray-800" />}
            {teams?.length ? (
              <>
                <TeamSwitcher teams={teams} view={{ currentTeamId, unread: chatUnread, mini }} />
                {slots?.teamPanel && <div className={cn(mini && 'md:hidden')}>{slots.teamPanel}</div>}
              </>
            ) : (
              <Link to={ROUTES.start} className={cn('px-3 py-2 text-sm font-medium text-brand-700 hover:underline dark:text-brand-300', mini && 'md:hidden')}>
                {t('sidebar.createTeam')}
              </Link>
            )}
          </div>

          <div className="mt-auto flex flex-col gap-0.5">
            <NavItem item={{ to: ROUTES.billing, icon: <CreditCard />, mini }}>
              {t('nav.billing')}
            </NavItem>
            <NavItem item={{ to: ROUTES.settings, icon: <Settings />, mini }}>
              {t('nav.settings')}
            </NavItem>
          </div>
        </nav>

        {slots?.footer && <div className={cn(mini && 'md:hidden')}>{slots.footer}</div>}
        <div className={cn('p-3', mini && 'md:hidden')}>
          <PlanUsage />
        </div>
      </aside>
    </>
  )
}
