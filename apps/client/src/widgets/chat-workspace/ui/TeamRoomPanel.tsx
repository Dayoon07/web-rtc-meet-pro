import { FolderPlus, Hash, MessageSquarePlus, Plus, Search } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Menu, MenuItem, Spinner } from '@/shared/ui'
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

/** + 버튼: 새 채널 · 새 1:1 채팅 · 새 카테고리. 채널·카테고리는 팀 소유자·관리자만이라, 아니면 바로 1:1 채팅 */
function AddChatButton() {
  const { t } = useTranslation()
  const ws = useWorkspace()
  if (!ws.canManage) {
    return (
      <Tool item={{ label: t('chat.newDirect'), onClick: () => ws.dialogs.direct.show() }}>
        <Plus className="size-4" />
      </Tool>
    )
  }
  return (
    <Menu
      label={t('sidebar.addChat')}
      panelClassName="w-48 p-1.5"
      trigger={({ open, toggle, id }) => (
        <button type="button" className={toolClass} aria-label={t('sidebar.addChat')} title={t('sidebar.addChat')} aria-expanded={open} aria-controls={id} onClick={toggle}>
          <Plus className="size-4" />
        </button>
      )}
    >
      {(close) => {
        // 메뉴를 닫고 그 창을 연다
        const pick = (open: () => void) => () => {
          close()
          open()
        }
        return (
          <>
            <MenuItem icon={<Hash />} onClick={pick(() => ws.dialogs.channel.show(null))}>
              {t('chat.group')}
            </MenuItem>
            <MenuItem icon={<MessageSquarePlus />} onClick={pick(() => ws.dialogs.direct.show())}>
              {t('chat.newDirect')}
            </MenuItem>
            <MenuItem icon={<FolderPlus />} onClick={pick(() => ws.dialogs.category.show(null))}>
              {t('chat.newCategory')}
            </MenuItem>
          </>
        )
      }}
    </Menu>
  )
}

/** "채팅 목록" 제목 줄: 메시지 검색 · 추가 */
function PanelHeader() {
  const { t } = useTranslation()
  const ws = useWorkspace()
  return (
    <div className="flex items-center gap-0.5 px-2 pt-2 pb-0.5">
      <span className="flex-1 text-xs font-semibold tracking-wide text-gray-400 uppercase">{t('sidebar.chatList')}</span>
      <Tool item={{ label: t('chat.search.open'), onClick: () => ws.dialogs.search.show() }}>
        <Search className="size-4" />
      </Tool>
      <AddChatButton />
    </div>
  )
}

/**
 * 앱 사이드바의 "채팅 목록": 지금 팀의 카테고리 → 채널, 1:1 대화를 짧은 줄로 (Slack 처럼 사이드바 하나로).
 * 누르면 그 방으로, + 로 채팅방을 몇 개든 만든다.
 */
export function TeamRoomPanel() {
  const ws = useChatWorkspace()
  if (ws.teamsPending || !ws.teams.length) return null
  return (
    <WorkspaceContext value={ws}>
      <RoomListStyleContext value="compact">
        {/* 휴대폰 서랍에서 묶음 접기·버튼을 눌러도 서랍이 닫히지 않게 (방으로 가면 주소가 바뀌어 닫힌다) */}
        <div className="mb-2" onClick={(e) => e.stopPropagation()}>
          <PanelHeader />
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
