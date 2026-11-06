import { useQuery } from '@tanstack/react-query'
import type { TFunction } from 'i18next'
import { http } from '@/shared/api'

export type ChatUser = { id: number; nickname: string; profileImageUrl: string | null }

export type ChatMessage = {
  id: number
  roomId: number
  sender: ChatUser | null
  type: 'TEXT' | 'SYSTEM'
  /** SYSTEM 이면 {"key","actor","targets"} JSON */
  content: string
  createdAt: string
}

export type ChatRoom = {
  id: number
  teamId: number
  type: 'DIRECT' | 'GROUP'
  name: string | null
  createdBy: number
  members: ChatUser[]
  lastMessage: ChatMessage | null
  unreadCount: number
  lastMessageAt: string | null
  createdAt: string
}

/** 서버 /user/queue/chat 이벤트 */
export type ChatEvent = { type: 'MESSAGE' | 'ROOM'; teamId: number; roomId: number; message: ChatMessage | null }

export const chatKeys = {
  unread: ['chat', 'unread'] as const,
  rooms: (teamId: number) => ['chat', 'rooms', teamId] as const,
  room: (roomId: number) => ['chat', 'room', roomId] as const,
  messages: (roomId: number) => ['chat', 'messages', roomId] as const,
}

export const chatApi = {
  unreadCount: () => http<{ count: number }>('/chat/unread-count'),
  rooms: (teamId: number) => http<ChatRoom[]>(`/teams/${teamId}/chat/rooms`),
  room: (roomId: number) => http<ChatRoom>(`/chat/rooms/${roomId}`),
  openDirect: (teamId: number, userId: number) =>
    http<ChatRoom>(`/teams/${teamId}/chat/rooms/direct`, { method: 'POST', json: { userId } }),
  createGroup: (teamId: number, name: string, memberIds: number[]) =>
    http<ChatRoom>(`/teams/${teamId}/chat/rooms/group`, { method: 'POST', json: { name, memberIds } }),
  invite: (roomId: number, userIds: number[]) => http<ChatRoom>(`/chat/rooms/${roomId}/members`, { method: 'POST', json: { userIds } }),
  leave: (roomId: number) => http<void>(`/chat/rooms/${roomId}/leave`, { method: 'POST' }),
  messages: (roomId: number, before?: number) =>
    http<ChatMessage[]>(`/chat/rooms/${roomId}/messages${before ? `?before=${before}` : ''}`),
  send: (roomId: number, content: string) => http<ChatMessage>(`/chat/rooms/${roomId}/messages`, { method: 'POST', json: { content } }),
  read: (roomId: number, messageId: number) => http<void>(`/chat/rooms/${roomId}/read`, { method: 'POST', json: { messageId } }),
}

export const useChatUnreadQuery = () =>
  useQuery({ queryKey: chatKeys.unread, queryFn: chatApi.unreadCount, select: (d) => d.count })

export const useChatRoomsQuery = (teamId: number) =>
  useQuery({ queryKey: chatKeys.rooms(teamId), queryFn: () => chatApi.rooms(teamId), enabled: teamId > 0 })

/** 1:1 방은 상대방 닉네임, 그룹 방은 방 이름 */
export function roomTitle(room: ChatRoom, myId: number | undefined) {
  if (room.type === 'GROUP') return room.name ?? ''
  return room.members.find((m) => m.id !== myId)?.nickname ?? room.members[0]?.nickname ?? ''
}

export const roomPeer = (room: ChatRoom, myId: number | undefined) =>
  room.type === 'DIRECT' ? room.members.find((m) => m.id !== myId) ?? null : null

/** SYSTEM 메시지 JSON → 현재 언어 문장 */
export function systemText(message: ChatMessage, t: TFunction, list: (items: string[]) => string): string {
  try {
    const { key, actor, targets } = JSON.parse(message.content) as { key: string; actor: string; targets: string[] }
    const joined = list(targets ?? [])
    switch (key) {
      case 'created':
        return targets?.length ? t('chat.system.createdWith', { actor, targets: joined }) : t('chat.system.created', { actor })
      case 'invited':
        return t('chat.system.invited', { actor, targets: joined })
      case 'left':
        return t('chat.system.left', { actor })
      default:
        return ''
    }
  } catch {
    return message.content
  }
}

/** 방 목록 미리보기 문장 */
export function previewText(message: ChatMessage | null, t: TFunction, list: (items: string[]) => string) {
  if (!message) return ''
  return message.type === 'SYSTEM' ? systemText(message, t, list) : message.content
}
