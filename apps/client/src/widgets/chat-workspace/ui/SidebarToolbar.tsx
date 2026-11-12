import { ChevronsUpDown, FolderPlus, Info, MessageSquarePlus, Plus, Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { useChatUnreadQuery } from '@/entities/chat'
import type { TeamSummary } from '@/entities/team'
import { ROUTES } from '@/shared/config/routes'
import { cn } from '@/shared/lib/cn'
import { Button, IconButton, Menu, MenuItem } from '@/shared/ui'
import { useWorkspace } from '../model/context'

/** 팀 첫 글자 네모 */
function TeamMark({ name }: { name: string }) {
  return (
    <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-gradient-to-br from-brand-400 to-brand-600 text-xs font-bold text-white">
      {name.slice(0, 1).toUpperCase()}
    </span>
  )
}

/** 지금 팀 이름 (+ 초대받은 팀이면 "초대받음") */
function TeamLabel({ team }: { team?: TeamSummary }) {
  const { t } = useTranslation()
  const name = team?.name ?? ''
  return (
    <>
      <TeamMark name={name} />
      <span className="min-w-0 flex-1 truncate text-left font-semibold text-gray-900 dark:text-white">{name}</span>
      {team && team.myRole !== 'OWNER' && (
        <span className="shrink-0 rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-semibold text-gray-500 dark:bg-gray-800 dark:text-gray-400">{t('sidebar.invited')}</span>
      )}
    </>
  )
}

/** 팀이 둘 이상일 때 (예전에 만든 팀·초대받은 팀): 눌러서 바꾼다. "내 팀" 과 "초대받은 팀" 을 나눠 보여 준다 */
function TeamPicker({ current }: { current?: TeamSummary }) {
  const { t } = useTranslation()
  const ws = useWorkspace()
  const navigate = useNavigate()
  const { data: unread } = useChatUnreadQuery()
  const others = ws.teams.filter((tm) => tm.id !== current?.id).reduce((n, tm) => n + (unread?.byTeam[tm.id] ?? 0), 0)
  const groups = [
    { title: t('sidebar.myTeam'), list: ws.teams.filter((tm) => tm.myRole === 'OWNER') },
    { title: t('sidebar.invitedTeams'), list: ws.teams.filter((tm) => tm.myRole !== 'OWNER') },
  ].filter((g) => g.list.length)
  return (
    <div className="min-w-0 flex-1">
      <Menu
        label={t('sidebar.switchTeam')}
        align="start"
        panelClassName="w-60 p-1.5"
        trigger={({ open, toggle, id }) => (
          <button
            type="button"
            onClick={toggle}
            aria-expanded={open}
            aria-controls={id}
            title={t('sidebar.switchTeam')}
            className="flex w-full items-center gap-2.5 rounded-lg px-1.5 py-1 hover:bg-gray-100 dark:hover:bg-gray-800"
          >
            <TeamLabel team={current} />
            {others > 0 && <span className="size-2 shrink-0 rounded-full bg-brand-600" />}
            <ChevronsUpDown className="size-4 shrink-0 text-gray-400" />
          </button>
        )}
      >
        {(close) =>
          groups.map((g) => (
            <div key={g.title} className="flex flex-col">
              <p className="px-3 pt-2 pb-1 text-[11px] font-semibold tracking-wide text-gray-400 uppercase">{g.title}</p>
              {g.list.map((tm) => {
                const count = unread?.byTeam[tm.id] ?? 0
                return (
                  <MenuItem
                    key={tm.id}
                    icon={<TeamMark name={tm.name} />}
                    onClick={() => {
                      close()
                      navigate(ROUTES.chat(tm.id))
                    }}
                  >
                    <span className={cn('min-w-0 flex-1 truncate', tm.id === current?.id && 'font-semibold text-brand-700 dark:text-brand-200')}>{tm.name}</span>
                    {count > 0 && <span className="rounded-full bg-brand-600 px-1.5 text-[11px] leading-5 font-bold text-white">{count > 99 ? '99+' : count}</span>}
                  </MenuItem>
                )
              })}
            </div>
          ))
        }
      </Menu>
    </div>
  )
}

/**
 * 방 목록 위: 지금 팀 (팀이 둘 이상이면 눌러서 바꾸기) · 팀 정보 + 새 1:1 채팅 · 새 채널 · 새 카테고리 · 메시지 검색.
 * 앱 사이드바와 휴대폰 채팅 화면의 방 목록이 같이 쓴다
 */
export function SidebarToolbar() {
  const { t } = useTranslation()
  const ws = useWorkspace()
  const current = ws.teams.find((tm) => tm.id === ws.activeTeamId)
  const infoLink = (
    <Link
      to={ROUTES.team(ws.activeTeamId)}
      title={t('chat.workspaceInfo')}
      className="flex shrink-0 items-center gap-1 rounded-md px-1.5 py-1 text-xs text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200"
    >
      <Info className="size-4" />
      <span>{t('chat.workspaceInfo')}</span>
    </Link>
  )
  return (
    <div className="flex flex-col gap-3 border-b border-gray-200 p-3 dark:border-gray-800">
      <div className="flex items-center gap-1">
        {ws.teams.length > 1 ? (
          <TeamPicker current={current} />
        ) : (
          <div className="flex min-w-0 flex-1 items-center gap-2.5 px-1.5 py-1">
            <TeamLabel team={current} />
          </div>
        )}
        {infoLink}
      </div>
      <div className="flex gap-2">
        <Button size="sm" variant="secondary" className="flex-1" icon={<MessageSquarePlus className="size-4" />} onClick={() => ws.dialogs.direct.show()}>
          {t('chat.newDirect')}
        </Button>
        <Button
          size="sm"
          variant="outline"
          className="flex-1"
          icon={<Plus className="size-4" />}
          disabled={!ws.canManage}
          title={ws.canManage ? undefined : t('chat.ownerOnlyGroup')}
          onClick={() => ws.dialogs.channel.show(null)}
        >
          {t('chat.group')}
        </Button>
        {ws.canManage && (
          <IconButton label={t('chat.newCategory')} title={t('chat.newCategory')} onClick={() => ws.dialogs.category.show(null)}>
            <FolderPlus className="size-5" />
          </IconButton>
        )}
        <IconButton label={t('chat.search.open')} title={t('chat.search.open')} onClick={() => ws.dialogs.search.show()}>
          <Search className="size-5" />
        </IconButton>
      </div>
    </div>
  )
}
