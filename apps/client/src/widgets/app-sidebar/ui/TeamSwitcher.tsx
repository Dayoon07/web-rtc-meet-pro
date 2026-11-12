import { ChevronsUpDown, Info } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import type { ChatUnread } from '@/entities/chat'
import type { TeamSummary } from '@/entities/team'
import { ROUTES } from '@/shared/config/routes'
import { cn } from '@/shared/lib/cn'
import { Menu, MenuItem } from '@/shared/ui'

/** 팀 첫 글자 네모 */
function TeamMark({ name, mini }: { name: string; mini?: boolean }) {
  return (
    <span
      className={cn(
        'flex size-7 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-brand-400 to-brand-600 text-xs font-bold text-white',
        mini && 'md:size-8',
      )}
    >
      {name.slice(0, 1).toUpperCase()}
    </span>
  )
}

/** 메뉴 한 칸: 팀 이름 + 안 읽은 수 */
function TeamItem({ team, view }: { team: TeamSummary; view: { unread?: number; current: boolean; pick: () => void } }) {
  return (
    <MenuItem icon={<TeamMark name={team.name} />} onClick={view.pick}>
      <span className={cn('min-w-0 flex-1 truncate', view.current && 'font-semibold text-brand-700 dark:text-brand-200')}>{team.name}</span>
      {!!view.unread && <span className="rounded-full bg-brand-600 px-1.5 text-[11px] leading-5 font-bold text-white">{view.unread > 99 ? '99+' : view.unread}</span>}
    </MenuItem>
  )
}

export type TeamSwitcherView = { currentTeamId?: number; unread?: ChatUnread; mini: boolean }

/**
 * 사이드바 위 지금 팀. 팀은 보통 하나 (내가 만든 팀, 또는 초대받아 들어간 팀).
 * 팀이 둘 이상이면 눌러서 바꾼다: "내 팀" 과 "초대받은 팀" 을 나눠 보여 준다. (i) = 팀 정보
 */
export function TeamSwitcher({ teams, view }: { teams: TeamSummary[]; view: TeamSwitcherView }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const current = teams.find((tm) => tm.id === view.currentTeamId) ?? teams[0]
  const owned = teams.filter((tm) => tm.myRole === 'OWNER')
  const invited = teams.filter((tm) => tm.myRole !== 'OWNER')
  const otherUnread = teams.filter((tm) => tm.id !== current.id).reduce((n, tm) => n + (view.unread?.byTeam[tm.id] ?? 0), 0)
  const invitedBadge = current.myRole !== 'OWNER' && (
    <span className="shrink-0 rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-semibold text-gray-500 dark:bg-gray-800 dark:text-gray-400">{t('sidebar.invited')}</span>
  )

  if (view.mini) {
    return (
      <Link to={ROUTES.chat(current.id)} title={current.name} className="hidden justify-center py-1 md:flex">
        <TeamMark name={current.name} mini />
      </Link>
    )
  }

  const label = (
    <>
      <TeamMark name={current.name} />
      <span className="min-w-0 flex-1 truncate text-left text-sm font-semibold text-gray-900 dark:text-white">{current.name}</span>
      {invitedBadge}
      {otherUnread > 0 && <span className="size-2 shrink-0 rounded-full bg-brand-600" title={t('sidebar.switchTeam')} />}
    </>
  )

  return (
    // 휴대폰 서랍에서 메뉴를 열어도 서랍이 닫히지 않게
    <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
      {teams.length > 1 ? (
        <div className="min-w-0 flex-1">
          <Menu
            label={t('sidebar.switchTeam')}
            align="start"
            panelClassName="w-56 p-1.5"
            trigger={({ open, toggle, id }) => (
              <button
                type="button"
                onClick={toggle}
                aria-expanded={open}
                aria-controls={id}
                title={t('sidebar.switchTeam')}
                className="flex w-full items-center gap-2.5 rounded-lg px-2 py-1.5 hover:bg-gray-100 dark:hover:bg-gray-800"
              >
                {label}
                <ChevronsUpDown className="size-4 shrink-0 text-gray-400" />
              </button>
            )}
          >
            {(close) => (
              <>
                {[
                  { title: t('sidebar.myTeam'), list: owned },
                  { title: t('sidebar.invitedTeams'), list: invited },
                ]
                  .filter((g) => g.list.length)
                  .map((g) => (
                    <div key={g.title} className="flex flex-col">
                      <p className="px-3 pt-2 pb-1 text-[11px] font-semibold tracking-wide text-gray-400 uppercase">{g.title}</p>
                      {g.list.map((tm) => (
                        <TeamItem
                          key={tm.id}
                          team={tm}
                          view={{
                            unread: view.unread?.byTeam[tm.id],
                            current: tm.id === current.id,
                            pick: () => {
                              close()
                              navigate(ROUTES.chat(tm.id))
                            },
                          }}
                        />
                      ))}
                    </div>
                  ))}
              </>
            )}
          </Menu>
        </div>
      ) : (
        <Link to={ROUTES.chat(current.id)} className="flex min-w-0 flex-1 items-center gap-2.5 rounded-lg px-2 py-1.5 hover:bg-gray-100 dark:hover:bg-gray-800">
          {label}
        </Link>
      )}
      <Link
        to={ROUTES.team(current.id)}
        aria-label={t('chat.workspaceInfo')}
        title={t('chat.workspaceInfo')}
        className="flex size-7 shrink-0 items-center justify-center rounded-md text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200"
      >
        <Info className="size-4" />
      </Link>
    </div>
  )
}
