import { CategoryModal, CreateGroupRoomModal, StartDirectChatModal } from '@/features/chat-room-actions'
import { MessageSearchModal } from '@/features/search-messages'
import { useWorkspace } from '../model/context'

/** 새 1:1 채팅 · 새 채널 · 카테고리 · 메시지 검색 창 (창은 화면 맨 위에 뜬다) */
export function WorkspaceDialogs() {
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
