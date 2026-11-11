import { FolderPlus, MessageSquarePlus, MessagesSquare, Plus, Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { CategoryModal, CreateGroupRoomModal, StartDirectChatModal } from '@/features/chat-room-actions'
import { MessageSearchModal } from '@/features/search-messages'
import { VoiceBar } from '@/features/voice-chat'
import { ROUTES } from '@/shared/config/routes'
import { cn } from '@/shared/lib/cn'
import { Button, EmptyState, IconButton, LinkButton, Select, Spinner } from '@/shared/ui'
import { useWorkspace, WorkspaceContext } from '../model/context'
import { useChatWorkspace } from '../model/use-chat-workspace'
import { RoomSidebar } from './RoomSidebar'
import { RoomView } from './RoomView'

/** 왼쪽 위: 워크스페이스 고르기 + 새 1:1 채팅 · 새 채널 · 새 카테고리 */
function SidebarToolbar() {
  const { t } = useTranslation()
  const ws = useWorkspace()
  return (
    <div className="flex flex-col gap-3 border-b border-gray-200 p-3 dark:border-gray-800">
      {ws.teams.length > 1 && (
        <Select aria-label={t('chat.team')} value={ws.activeTeamId} onChange={(e) => ws.goTeam(Number(e.target.value))}>
          {ws.teams.map((tm) => (
            <option key={tm.id} value={tm.id}>
              {tm.name}
            </option>
          ))}
        </Select>
      )}
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

/** 새 1:1 채팅 · 새 채널 · 카테고리 창 */
function WorkspaceDialogs() {
  const ws = useWorkspace()
  if (!ws.team || !ws.me) return null
  return (
    <>
      <StartDirectChatModal teamId={ws.activeTeamId} dialog={ws.dialogs.direct} />
      <CreateGroupRoomModal teamId={ws.activeTeamId} dialog={ws.dialogs.channel} />
      <CategoryModal teamId={ws.activeTeamId} dialog={ws.dialogs.category} />
      <MessageSearchModal teamId={ws.activeTeamId} dialog={ws.dialogs.search} />
    </>
  )
}

/**
 * 채팅 화면: 왼쪽 방 목록 (워크스페이스 선택 + 1:1/채널 만들기), 오른쪽 대화.
 * 워크스페이스·방은 주소(/app/chat/:teamId/:roomId)에서 읽는다. 실시간 메시지는 /user/queue/chat 으로 받는다.
 */
export function ChatWorkspace() {
  const { t } = useTranslation()
  const ws = useChatWorkspace()

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

  return (
    <WorkspaceContext value={ws}>
      {/* 슬랙·디스코드처럼 화면에 붙인다: 왼쪽 앱 사이드바 바로 옆에 방 목록, 입력창은 맨 아래 */}
      <div className="flex min-h-0 flex-1 overflow-hidden border-t border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-950">
        <aside className={cn('flex w-full flex-col border-gray-200 md:w-80 md:border-r dark:border-gray-800', ws.activeRoom && 'hidden md:flex')}>
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
