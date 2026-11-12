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
  /** 채널 만들기 (OWNER·ADMIN). categoryId 가 있으면 그 카테고리 안에 */
  createGroup: (teamId: number, name: string, memberIds: number[], categoryId?: number | null) =>
    request<ChatRoom>(API.teams.chatGroup(teamId), {
      method: 'POST',
      json: { name, memberIds, categoryId: categoryId ?? null } satisfies CreateGroupRequest,
    }),
  /** 채널 이름 바꾸기 · 카테고리 옮기기 (categoryId null = 카테고리 밖) */
  updateChannel: (roomId: number, body: UpdateChannelRequest) => request<ChatRoom>(API.chat.room(roomId), { method: 'PATCH', json: body }),
  categories: (teamId: number) => request<ChatCategory[]>(API.teams.chatCategories(teamId)),
  /** 채널 상시 음성방 들어가기 (진행 중인 음성방, 없으면 새로) */
  joinVoice: (roomId: number) => request<{ id: number }>(API.chat.voice(roomId), { method: 'POST' }),
  createCategory: (teamId: number, name: string) =>
    request<ChatCategory>(API.teams.chatCategories(teamId), { method: 'POST', json: { name } satisfies CategoryRequest }),
  renameCategory: (categoryId: number, name: string) =>
    request<ChatCategory>(API.chat.category(categoryId), { method: 'PATCH', json: { name } satisfies CategoryRequest }),
  /** 카테고리 지우기 (안의 채널은 카테고리 밖으로) */
  deleteCategory: (categoryId: number) => request<void>(API.chat.category(categoryId), { method: 'DELETE' }),
  invite: (roomId: number, userIds: number[]) =>
    request<ChatRoom>(API.chat.members(roomId), { method: 'POST', json: { userIds } satisfies InviteMembersRequest }),
  leave: (roomId: number) => request<void>(API.chat.leave(roomId), { method: 'POST' }),
  /** 공지 올리기·고치기 (content 가 null 이면 내리기) */
  setNotice: (roomId: number, content: string | null) =>
    request<ChatRoom>(API.chat.notice(roomId), { method: 'PUT', json: { content } satisfies NoticeRequest }),
  messages: (roomId: number, before?: number) => request<ChatMessage[]>(API.chat.messages(roomId, before)),
  /** 스레드 창: 시작 글 + 답글 (답글 id 를 줘도 그 스레드) */
  thread: (messageId: number) => request<ChatThread>(API.chat.thread(messageId)),
  /** 글 보내기. options = 답장할 메시지 · @멘션한 사람 · @모두 · 스레드 답글 */
  send: (roomId: number, content: string, options: SendOptions = {}) =>
    request<ChatMessage>(API.chat.messages(roomId), { method: 'POST', json: { content, ...options } satisfies SendMessageRequest }),
  /** 이 방 알림 끄기·켜기 (나만) */
  setMuted: (roomId: number, muted: boolean) => request<ChatRoom>(API.chat.mute(roomId), { method: 'PUT', json: { muted } satisfies MuteRequest }),
  /** "입력 중..." 알리기 (몇 초마다) */
  typing: (roomId: number) => request<void>(API.chat.typing(roomId), { method: 'POST' }),
  /** 이모지 반응 달기·떼기 (다시 누르면 떼어진다) */
  react: (messageId: number, emoji: string) =>
    request<ChatMessage>(API.chat.reactions(messageId), { method: 'POST', json: { emoji } satisfies ReactionRequest }),
  /** 워크스페이스에서 내가 들어간 방들의 메시지 검색 (최신 50개) */
  search: (teamId: number, q: string) => request<ChatMessage[]>(API.teams.chatSearch(teamId, q)),
  /** 파일 여러 개 + (선택) 함께 보낼 글 = FILE 메시지 1개. threadRootId 가 있으면 그 스레드에 */
  sendFiles: (roomId: number, files: File[], content?: string, threadRootId?: number | null) => {
    const form = new FormData()
    files.forEach((f) => form.append('files', f, f.name))
    if (content?.trim()) form.append('content', content.trim())
    if (threadRootId) form.append('threadRootId', String(threadRootId))
    return request<ChatMessage>(API.chat.files(roomId), { method: 'POST', form })
  },
  /** 내가 보낸 글 메시지 고치기 */
  edit: (messageId: number, content: string) =>
    request<ChatMessage>(API.chat.message(messageId), { method: 'PATCH', json: { content } satisfies SendMessageRequest }),
  /** 지우기 (보낸 사람, 또는 팀 OWNER·ADMIN) */
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
