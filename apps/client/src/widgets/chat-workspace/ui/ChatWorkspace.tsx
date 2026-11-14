import { MessagesSquare } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Navigate, useParams } from 'react-router'
import { PushPromptBanner } from '@/features/notification-settings'
import { VoiceBar } from '@/features/voice-chat'
import { ROUTES } from '@/shared/config/routes'
import { cn } from '@/shared/lib/cn'
import { EmptyState, LinkButton, Spinner } from '@/shared/ui'
import { WorkspaceContext } from '../model/context'
import { useChatWorkspace } from '../model/use-chat-workspace'
import { RoomSidebar } from './RoomSidebar'
import { RoomView } from './RoomView'
import { SidebarToolbar } from './SidebarToolbar'
import { WorkspaceDialogs } from './WorkspaceDialogs'

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

export function ChatWorkspace() {
  const { t } = useTranslation()
  const ws = useChatWorkspace()
  const { teamId } = useParams()
  const listClass = cn('w-full flex-col md:hidden', ws.activeRoom ? 'hidden' : 'flex')

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

  if (!teamId) return <Navigate to={ROUTES.chat(ws.activeTeamId)} replace />

  return (
    <WorkspaceContext value={ws}>
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
          <PushPromptBanner />
          {ws.activeRoom ? <RoomView key={ws.activeRoom.id} room={ws.activeRoom} /> : <NoRoomSelected />}
        </section>
        <WorkspaceDialogs />
      </div>
    </WorkspaceContext>
  )
}
