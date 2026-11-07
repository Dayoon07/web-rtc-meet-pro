import { useQuery } from '@tanstack/react-query'
import type { TFunction } from 'i18next'
import { http } from '@/shared/api'

export type ChatUser = { id: number; nickname: string; profileImageUrl: string | null }

/** url: 서명된 내려받기 주소 (12~24시간 유효, 방 메시지를 다시 불러오면 새 주소). image: 바로 미리보기 가능한 이미지 */
export type ChatFile = { id: number; name: string; size: number; contentType: string; image: boolean; url: string }

export type ChatMessage = {
  id: number
  roomId: number
  sender: ChatUser | null
  type: 'TEXT' | 'FILE' | 'SYSTEM'
  /** SYSTEM 이면 {"key","actor","targets"} JSON. FILE 은 글 없이 파일만 보내면 null */
  content: string | null
  files: ChatFile[]
  createdAt: string
  /** 보낸 사람이 고친 글 ("수정됨") */
  edited?: boolean
  /** 지운 메시지: content 는 null, files 는 비어 있다 */
  deleted?: boolean
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

/** 서버 /user/queue/chat 이벤트. MESSAGE_UPDATED = 고치거나 지운 메시지 (같은 id 를 바꿔 끼운다) */
export type ChatEvent = { type: 'MESSAGE' | 'MESSAGE_UPDATED' | 'ROOM'; teamId: number; roomId: number; message: ChatMessage | null }

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
  /** 파일 여러 개 + (선택) 함께 보낼 글 = FILE 메시지 1개 */
  sendFiles: (roomId: number, files: File[], content?: string) => {
    const form = new FormData()
    files.forEach((f) => form.append('files', f, f.name))
    if (content?.trim()) form.append('content', content.trim())
    return http<ChatMessage>(`/chat/rooms/${roomId}/files`, { method: 'POST', form })
  },
  /** 내가 보낸 글 메시지 고치기 */
  edit: (messageId: number, content: string) => http<ChatMessage>(`/chat/messages/${messageId}`, { method: 'PATCH', json: { content } }),
  /** 지우기 (보낸 사람, 또는 팀 OWNER·ADMIN) */
  remove: (messageId: number) => http<ChatMessage>(`/chat/messages/${messageId}`, { method: 'DELETE' }),
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

/** 서버 ChatService 와 같은 제한. 보내기 전에 화면에서 먼저 확인한다 */
export const CHAT_FILE_LIMITS = {
  maxFiles: 10,
  maxFileMb: 20,
  maxTotalMb: 50,
  blockedExtensions: ['exe', 'msi', 'bat', 'cmd', 'com', 'scr', 'pif', 'cpl', 'vbs', 'vbe', 'wsf', 'wsh', 'ps1', 'hta', 'lnk', 'reg'],
} as const

/** 첨부할 파일 목록 확인. 문제가 있으면 안내 문장, 없으면 null */
export function checkChatFiles(files: File[], t: TFunction): string | null {
  const { maxFiles, maxFileMb, maxTotalMb, blockedExtensions } = CHAT_FILE_LIMITS
  if (files.length > maxFiles) return t('chat.fileErrors.tooMany', { max: maxFiles })
  for (const f of files) {
    const ext = f.name.includes('.') ? f.name.split('.').pop()!.toLowerCase() : ''
    if ((blockedExtensions as readonly string[]).includes(ext)) return t('chat.fileErrors.blocked', { name: f.name, ext })
    if (f.size === 0) return t('chat.fileErrors.empty', { name: f.name })
    if (f.size > maxFileMb * 1024 * 1024) return t('chat.fileErrors.tooLarge', { name: f.name, max: maxFileMb })
  }
  if (files.reduce((sum, f) => sum + f.size, 0) > maxTotalMb * 1024 * 1024) return t('chat.fileErrors.totalTooLarge', { max: maxTotalMb })
  return null
}

/** 파일만 보낸 메시지의 미리보기: 사진 / 사진 3장 / 파일 / 파일 2개 */
export function filesSummary(files: ChatFile[], t: TFunction): string {
  const n = files.length
  if (files.every((f) => f.image)) return n === 1 ? t('chat.file.photo') : t('chat.file.photos', { count: n })
  return n === 1 ? t('chat.file.one') : t('chat.file.many', { count: n })
}

/** SYSTEM 메시지 JSON → 현재 언어 문장 */
export function systemText(message: ChatMessage, t: TFunction, list: (items: string[]) => string): string {
  try {
    const { key, actor, targets } = JSON.parse(message.content ?? '') as { key: string; actor: string; targets: string[] }
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
    return message.content ?? ''
  }
}

/** 방 목록 미리보기 문장 */
export function previewText(message: ChatMessage | null, t: TFunction, list: (items: string[]) => string) {
  if (!message) return ''
  if (message.deleted) return t('chat.deletedMessage')
  if (message.type === 'SYSTEM') return systemText(message, t, list)
  if (message.type === 'FILE') return message.content || filesSummary(message.files, t)
  return message.content ?? ''
}
