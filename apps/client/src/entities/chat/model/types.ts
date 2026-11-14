export type ChatUser = { id: number; nickname: string; profileImageUrl: string | null }

export type ChatFile = { id: number; name: string; size: number; contentType: string; image: boolean; url: string }

export type ChatMessage = {
  id: number
  roomId: number
  sender: ChatUser | null
  type: 'TEXT' | 'FILE' | 'SYSTEM'
  content: string | null
  files: ChatFile[]
  createdAt: string
  edited?: boolean
  deleted?: boolean
  replyTo?: ReplyPreview | null
  reactions?: Reaction[]
  mentions?: number[]
  mentionAll?: boolean
  threadRootId?: number | null
  replyCount?: number
  lastReplyAt?: string | null
  threadUsers?: ChatUser[]
}

export type ChatThread = { root: ChatMessage; replies: ChatMessage[] }

export type ReplyPreview = { id: number; sender: ChatUser | null; content: string | null; type: ChatMessage['type']; fileCount: number; deleted: boolean }

export type Reaction = { emoji: string; count: number; userIds: number[] }

export type ReadState = { userId: number; lastReadMessageId: number | null }

export type ChatNotice = { content: string; by: ChatUser | null; at: string | null }

export type ChatRoom = {
  id: number
  teamId: number
  type: 'DIRECT' | 'GROUP'
  categoryId: number | null
  voice?: { meetingId: number; participants: ChatUser[] } | null
  name: string | null
  createdBy: number
  members: ChatUser[]
  lastMessage: ChatMessage | null
  unreadCount: number
  lastMessageAt: string | null
  createdAt: string
  notice?: ChatNotice | null
  reads?: ReadState[]
  muted?: boolean
}

export type ChatCategory = { id: number; name: string; sortOrder: number }

export type ChatEvent = {
  type: 'MESSAGE' | 'MESSAGE_UPDATED' | 'ROOM' | 'CATEGORIES' | 'READ' | 'TYPING'
  teamId: number
  roomId: number
  message: ChatMessage | null
  read?: ReadState | null
  typing?: ChatUser | null
}

export type MeetingNotesCard = { meetingId: number; title: string; summary: string[]; decisions: number; actions: number }

export type OpenDirectRequest = { userId: number }
export type CreateGroupRequest = { name: string; memberIds: number[]; categoryId: number | null }
export type UpdateChannelRequest = { name?: string; categoryId?: number | null; moveCategory?: boolean }
export type CategoryRequest = { name: string }
export type InviteMembersRequest = { userIds: number[] }
export type NoticeRequest = { content: string | null }
export type SendMessageRequest = { content: string; replyToId?: number | null; mentionIds?: number[]; mentionAll?: boolean; threadRootId?: number | null }
export type SendOptions = Omit<SendMessageRequest, 'content'>
export type ReactionRequest = { emoji: string }
export type MuteRequest = { muted: boolean }
export type MarkReadRequest = { messageId: number }
export type UnreadCountResponse = { count: number; teams?: { teamId: number; count: number }[] }
export type ChatUnread = { total: number; byTeam: Record<number, number> }
