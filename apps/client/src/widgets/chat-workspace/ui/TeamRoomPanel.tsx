import { Spinner } from '@/shared/ui'
import { WorkspaceContext } from '../model/context'
import { useChatWorkspace } from '../model/use-chat-workspace'
import { RoomSidebar } from './RoomSidebar'
import { SidebarToolbar } from './SidebarToolbar'
import { WorkspaceDialogs } from './WorkspaceDialogs'

export function TeamRoomPanel() {
  const ws = useChatWorkspace()
  if (ws.teamsPending || !ws.teams.length) return null
  return (
    <WorkspaceContext value={ws}>
      <div className="-mx-3 flex min-h-0 flex-1 flex-col border-y border-gray-200 dark:border-gray-800" onClick={(e) => e.stopPropagation()}>
        <div className="shrink-0">
          <SidebarToolbar />
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto">
          {ws.roomsPending ? (
            <div className="flex justify-center py-6">
              <Spinner className="size-5 text-gray-400" />
            </div>
          ) : (
            <RoomSidebar />
          )}
        </div>
      </div>
      <WorkspaceDialogs />
    </WorkspaceContext>
  )
}
