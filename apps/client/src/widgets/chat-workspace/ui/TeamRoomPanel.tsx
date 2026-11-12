import { FolderPlus, Info, MessageSquarePlus, Plus, Search } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { ROUTES } from '@/shared/config/routes'
import { Spinner } from '@/shared/ui'
import { RoomListStyleContext, useWorkspace, WorkspaceContext } from '../model/context'
import { useChatWorkspace } from '../model/use-chat-workspace'
import { RoomSidebar } from './RoomSidebar'
import { WorkspaceDialogs } from './WorkspaceDialogs'

const toolClass =
  'flex size-7 items-center justify-center rounded-md text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200'

/** 작은 버튼 하나: item = 이름(마우스를 올리면 보임) · 누를 때, children = 아이콘 */
function Tool({ item, children }: { item: { label: string; onClick: () => void }; children: ReactNode }) {
  return (
    <button type="button" className={toolClass} aria-label={item.label} title={item.label} onClick={item.onClick}>
      {children}
    </button>
  )
}

/** 팀 이름 아래 버튼 줄: 새 1:1 채팅 · (관리자) 새 채널 · 새 카테고리 · 메시지 검색 · 팀 정보 */
function PanelTools() {
  const { t } = useTranslation()
  const ws = useWorkspace()
  return (
    <div className="flex items-center gap-0.5 pl-1">
      <Tool item={{ label: t('chat.newDirect'), onClick: () => ws.dialogs.direct.show() }}>
        <MessageSquarePlus className="size-4" />
      </Tool>
      {ws.canManage && (
        <>
          <Tool item={{ label: t('chat.group'), onClick: () => ws.dialogs.channel.show(null) }}>
            <Plus className="size-4" />
          </Tool>
          <Tool item={{ label: t('chat.newCategory'), onClick: () => ws.dialogs.category.show(null) }}>
            <FolderPlus className="size-4" />
          </Tool>
        </>
      )}
      <Tool item={{ label: t('chat.search.open'), onClick: () => ws.dialogs.search.show() }}>
        <Search className="size-4" />
      </Tool>
      <Link to={ROUTES.team(ws.activeTeamId)} className={toolClass} aria-label={t('chat.workspaceInfo')} title={t('chat.workspaceInfo')}>
        <Info className="size-4" />
      </Link>
    </div>
  )
}

/**
 * 앱 사이드바 "내 팀" 에서 지금 팀 아래에 펼치는 방 목록 (Slack 처럼 사이드바 하나로).
 * 카테고리 → 채널, 1:1 대화를 짧은 줄로 보여 주고, 누르면 그 방으로 간다.
 */
export function TeamRoomPanel() {
  const ws = useChatWorkspace()
  if (ws.teamsPending || !ws.teams.length) return null
  return (
    <WorkspaceContext value={ws}>
      <RoomListStyleContext value="compact">
        {/* 휴대폰 서랍에서 묶음 접기·버튼을 눌러도 서랍이 닫히지 않게 (방으로 가면 주소가 바뀌어 닫힌다) */}
        <div className="mt-0.5 mb-2 ml-3 border-l border-gray-200 pl-1 dark:border-gray-800" onClick={(e) => e.stopPropagation()}>
          <PanelTools />
          {ws.roomsPending ? (
            <div className="flex justify-center py-3">
              <Spinner className="size-4 text-gray-400" />
            </div>
          ) : (
            <RoomSidebar />
          )}
        </div>
        <WorkspaceDialogs />
      </RoomListStyleContext>
    </WorkspaceContext>
  )
}
