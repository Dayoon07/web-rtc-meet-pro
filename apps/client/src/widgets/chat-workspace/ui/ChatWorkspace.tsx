import { FolderPlus, Info, MessageSquarePlus, MessagesSquare, Plus, Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate, useParams } from 'react-router'
import { VoiceBar } from '@/features/voice-chat'
import { useAppShell } from '@/shared/lib/app-shell'
import { ROUTES } from '@/shared/config/routes'
import { cn } from '@/shared/lib/cn'
import { Button, EmptyState, IconButton, LinkButton, Spinner } from '@/shared/ui'
import { useWorkspace, WorkspaceContext } from '../model/context'
import { useChatWorkspace } from '../model/use-chat-workspace'
import { RoomSidebar } from './RoomSidebar'
import { RoomView } from './RoomView'
import { WorkspaceDialogs } from './WorkspaceDialogs'

/**
 * 왼쪽 위: 지금 워크스페이스 이름 (누르면 워크스페이스 정보 화면) + 새 1:1 채팅 · 새 채널 · 새 카테고리.
 * 다른 워크스페이스는 앱 왼쪽 사이드바의 "내 워크스페이스" 에서 고른다 (디스코드의 서버 목록처럼)
 */
function SidebarToolbar() {
  const { t } = useTranslation()
  const ws = useWorkspace()
  const name = ws.teams.find((tm) => tm.id === ws.activeTeamId)?.name ?? ''
  return (
    <div className="flex flex-col gap-3 border-b border-gray-200 p-3 dark:border-gray-800">
      <Link
        to={ROUTES.team(ws.activeTeamId)}
        title={t('chat.workspaceInfo')}
        className="group flex items-center gap-2.5 rounded-lg px-1.5 py-1 hover:bg-gray-100 dark:hover:bg-gray-800"
      >
        <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-gradient-to-br from-brand-400 to-brand-600 text-xs font-bold text-white">
          {name.slice(0, 1).toUpperCase()}
        </span>
        <span className="min-w-0 flex-1 truncate font-semibold text-gray-900 dark:text-white">{name}</span>
        <span className="flex shrink-0 items-center gap-1 text-xs text-gray-400 group-hover:text-gray-700 dark:group-hover:text-gray-200">
          <Info className="size-4" />
          <span className="hidden sm:inline">{t('chat.workspaceInfo')}</span>
        </span>
      </Link>
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

/** 방을 고르지 않았을 때 오른쪽 */
function NoRoomSelected() {
  const { t } = useTranslation()
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300">
        <MessagesSquare className="size-7" />
      </span>
      <p className="font-semibold text-gray-900 dark:text-white">{t('chat.selectRoom')}</p>
      <p className="max-w-xs text-sm text-gray-500 dark:text-gray-400">{t('chat.selectRoomHint')}</p>
    </div>
  )
}

/**
 * 채팅 화면: 왼쪽 방 목록 (워크스페이스 선택 + 1:1/채널 만들기), 오른쪽 대화.
 * 워크스페이스·방은 주소(/app/chat/:teamId/:roomId)에서 읽는다. 실시간 메시지는 /user/queue/chat 으로 받는다.
 */
export function ChatWorkspace() {
  const { t } = useTranslation()
  const ws = useChatWorkspace()
  const { teamId } = useParams()
  const { sidebarCollapsed } = useAppShell()
  // 방 목록은 보통 앱 사이드바 "내 팀" 아래에 있다. 여기 가운데 목록은
  // 휴대폰에서 방을 고르기 전, 또는 데스크톱에서 사이드바를 접었을 때만 보인다
  const listClass = cn(
    'w-full flex-col border-gray-200 md:w-80 md:border-r dark:border-gray-800',
    ws.activeRoom ? 'hidden' : 'flex',
    sidebarCollapsed ? 'md:flex' : 'md:hidden',
  )

  if (ws.teamsPending) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <Spinner className="size-6 text-gray-400" />
      </div>
    )
  }
  if (!ws.teams.length) {
    return (
      <div className="mx-auto w-full max-w-lg p-6">
        <EmptyState
          icon={<MessagesSquare />}
          title={t('chat.noTeams')}
          description={t('chat.noTeamsHint')}
          action={<LinkButton to={ROUTES.teams}>{t('chat.goTeams')}</LinkButton>}
        />
      </div>
    )
  }

  // /app/chat 처럼 워크스페이스가 없으면 첫 워크스페이스 채팅으로 (다른 곳은 왼쪽 사이드바에서 고른다)
  if (!teamId) return <Navigate to={ROUTES.chat(ws.activeTeamId)} replace />

  return (
    <WorkspaceContext value={ws}>
      {/* 슬랙·디스코드처럼 화면에 붙인다: 왼쪽 앱 사이드바 바로 옆에 방 목록, 입력창은 맨 아래 */}
      <div className="flex min-h-0 flex-1 overflow-hidden border-t border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-950">
        <aside className={listClass}>
          <SidebarToolbar />
          <div className="flex-1 overflow-y-auto">
            {ws.roomsPending ? (
              <div className="flex justify-center py-10">
                <Spinner className="size-5 text-gray-400" />
              </div>
            ) : (
              <RoomSidebar />
            )}
          </div>
          <VoiceBar me={ws.me} />
        </aside>
        <section className={cn('flex min-w-0 flex-1 flex-col', !ws.activeRoom && 'hidden md:flex')}>
          {ws.activeRoom ? <RoomView key={ws.activeRoom.id} room={ws.activeRoom} /> : <NoRoomSelected />}
        </section>
        <WorkspaceDialogs />
      </div>
    </WorkspaceContext>
  )
}
