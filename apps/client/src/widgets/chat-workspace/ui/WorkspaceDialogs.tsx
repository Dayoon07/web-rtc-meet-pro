import { CategoryModal, CreateGroupRoomModal, StartDirectChatModal } from '@/features/chat-room-actions'
import { MessageSearchModal } from '@/features/search-messages'
import { useWorkspace } from '../model/context'

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
