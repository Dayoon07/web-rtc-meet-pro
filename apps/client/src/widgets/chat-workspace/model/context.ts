import { createContext, useContext } from 'react'
import type { ChatWorkspaceModel } from './use-chat-workspace'
import type { RoomViewModel } from './use-room-view'

/** 채팅 화면 전체 상태 (useChatWorkspace). ChatWorkspace 가 넣어 준다 */
export const WorkspaceContext = createContext<ChatWorkspaceModel | null>(null)

/** 열린 방 상태 (useRoomView). RoomView 가 넣어 준다 */
export const RoomContext = createContext<RoomViewModel | null>(null)

export function useWorkspace(): ChatWorkspaceModel {
  const value = useContext(WorkspaceContext)
  if (!value) throw new Error('useWorkspace 는 ChatWorkspace 안에서만 쓸 수 있습니다')
  return value
}

export function useRoom(): RoomViewModel {
  const value = useContext(RoomContext)
  if (!value) throw new Error('useRoom 은 RoomView 안에서만 쓸 수 있습니다')
  return value
}
