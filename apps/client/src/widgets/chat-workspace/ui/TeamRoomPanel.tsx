import { Spinner } from '@/shared/ui'
import { WorkspaceContext } from '../model/context'
import { useChatWorkspace } from '../model/use-chat-workspace'
import { RoomSidebar } from './RoomSidebar'
import { SidebarToolbar } from './SidebarToolbar'
import { WorkspaceDialogs } from './WorkspaceDialogs'

/**
 * 앱 사이드바의 채팅 목록: 지금 팀 · 새 1:1 채팅 · 채널 · 카테고리 · 검색, 그 아래 카테고리 → 채널, 1:1 대화
 * (방마다 사진·마지막 메시지·시간·안 읽은 수). 누르면 그 방으로.
 */
export function TeamRoomPanel() {
  const ws = useChatWorkspace()
  if (ws.teamsPending || !ws.teams.length) return null
  return (
    <WorkspaceContext value={ws}>
      {/* 휴대폰 서랍에서 묶음 접기·버튼을 눌러도 서랍이 닫히지 않게 (방으로 가면 주소가 바뀌어 닫힌다) */}
      <div className="-mx-3 flex min-h-0 flex-1 flex-col border-y border-gray-200 dark:border-gray-800" onClick={(e) => e.stopPropagation()}>
        {/* 지금 팀·버튼 줄은 고정, 방 목록만 스크롤 */}
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
