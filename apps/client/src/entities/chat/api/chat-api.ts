import { useQuery } from '@tanstack/react-query'
import { API, request } from '@/shared/api'
import type {
  CategoryRequest,
  ChatCategory,
  ChatMessage,
  ChatRoom,
  CreateGroupRequest,
  InviteMembersRequest,
  MarkReadRequest,
  NoticeRequest,
  OpenDirectRequest,
  MuteRequest,
  ReactionRequest,
  SendOptions,
  ChatThread,
  SendMessageRequest,
  ChatUnread,
  UnreadCountResponse,
  UpdateChannelRequest,
} from '../model/types'

export const chatKeys = {
  unread: ['chat', 'unread'] as const,
  rooms: (teamId: number) => ['chat', 'rooms', teamId] as const,
  categories: (teamId: number) => ['chat', 'categories', teamId] as const,
  room: (roomId: number) => ['chat', 'room', roomId] as const,
  messages: (roomId: number) => ['chat', 'messages', roomId] as const,
  thread: (rootId: number) => ['chat', 'thread', rootId] as const,
}

export const chatApi = {
  unreadCount: () => request<UnreadCountResponse>(API.chat.unreadCount),
  rooms: (teamId: number) => request<ChatRoom[]>(API.teams.chatRooms(teamId)),
  room: (roomId: number) => request<ChatRoom>(API.chat.room(roomId)),
  openDirect: (teamId: number, userId: number) =>
    request<ChatRoom>(API.teams.chatDirect(teamId), { method: 'POST', json: { userId } satisfies OpenDirectRequest }),
  createGroup: (teamId: number, name: string, memberIds: number[], categoryId?: number | null) =>
    request<ChatRoom>(API.teams.chatGroup(teamId), {
      method: 'POST',
      json: { name, memberIds, categoryId: categoryId ?? null } satisfies CreateGroupRequest,
    }),
  updateChannel: (roomId: number, body: UpdateChannelRequest) => request<ChatRoom>(API.chat.room(roomId), { method: 'PATCH', json: body }),
  categories: (teamId: number) => request<ChatCategory[]>(API.teams.chatCategories(teamId)),
  joinVoice: (roomId: number) => request<{ id: number }>(API.chat.voice(roomId), { method: 'POST' }),
  createCategory: (teamId: number, name: string) =>
    request<ChatCategory>(API.teams.chatCategories(teamId), { method: 'POST', json: { name } satisfies CategoryRequest }),
  renameCategory: (categoryId: number, name: string) =>
    request<ChatCategory>(API.chat.category(categoryId), { method: 'PATCH', json: { name } satisfies CategoryRequest }),
  deleteCategory: (categoryId: number) => request<void>(API.chat.category(categoryId), { method: 'DELETE' }),
  invite: (roomId: number, userIds: number[]) =>
    request<ChatRoom>(API.chat.members(roomId), { method: 'POST', json: { userIds } satisfies InviteMembersRequest }),
  leave: (roomId: number) => request<void>(API.chat.leave(roomId), { method: 'POST' }),
  setNotice: (roomId: number, content: string | null) =>
    request<ChatRoom>(API.chat.notice(roomId), { method: 'PUT', json: { content } satisfies NoticeRequest }),
  messages: (roomId: number, before?: number) => request<ChatMessage[]>(API.chat.messages(roomId, before)),
  thread: (messageId: number) => request<ChatThread>(API.chat.thread(messageId)),
  send: (roomId: number, content: string, options: SendOptions = {}) =>
    request<ChatMessage>(API.chat.messages(roomId), { method: 'POST', json: { content, ...options } satisfies SendMessageRequest }),
  setMuted: (roomId: number, muted: boolean) => request<ChatRoom>(API.chat.mute(roomId), { method: 'PUT', json: { muted } satisfies MuteRequest }),
  typing: (roomId: number) => request<void>(API.chat.typing(roomId), { method: 'POST' }),
  react: (messageId: number, emoji: string) =>
    request<ChatMessage>(API.chat.reactions(messageId), { method: 'POST', json: { emoji } satisfies ReactionRequest }),
  search: (teamId: number, q: string) => request<ChatMessage[]>(API.teams.chatSearch(teamId, q)),
  sendFiles: (roomId: number, files: File[], content?: string, threadRootId?: number | null) => {
    const form = new FormData()
    files.forEach((f) => form.append('files', f, f.name))
    if (content?.trim()) form.append('content', content.trim())
    if (threadRootId) form.append('threadRootId', String(threadRootId))
    return request<ChatMessage>(API.chat.files(roomId), { method: 'POST', form })
  },
  edit: (messageId: number, content: string) =>
    request<ChatMessage>(API.chat.message(messageId), { method: 'PATCH', json: { content } satisfies SendMessageRequest }),
  remove: (messageId: number) => request<ChatMessage>(API.chat.message(messageId), { method: 'DELETE' }),
  read: (roomId: number, messageId: number) =>
    request<void>(API.chat.read(roomId), { method: 'POST', json: { messageId } satisfies MarkReadRequest }),
}

export const useChatUnreadQuery = () =>
  useQuery({
    queryKey: chatKeys.unread,
    queryFn: chatApi.unreadCount,
    select: (d): ChatUnread => ({ total: d.count, byTeam: Object.fromEntries((d.teams ?? []).map((x) => [x.teamId, x.count])) }),
  })

export const useChatRoomsQuery = (teamId: number) =>
  useQuery({ queryKey: chatKeys.rooms(teamId), queryFn: () => chatApi.rooms(teamId), enabled: teamId > 0 })

export const useChatCategoriesQuery = (teamId: number) =>
  useQuery({ queryKey: chatKeys.categories(teamId), queryFn: () => chatApi.categories(teamId), enabled: teamId > 0 })
