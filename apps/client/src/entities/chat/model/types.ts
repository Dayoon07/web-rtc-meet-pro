// 채팅 자료 모양. 서버가 돌려주는 값(응답)과 서버에 보내는 값(XxxRequest)

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
  /** 답장이면 원래 메시지 미리보기 */
  replyTo?: ReplyPreview | null
  /** 이모지 반응 (먼저 단 것부터) */
  reactions?: Reaction[]
  /** @멘션한 사람 user id */
  mentions?: number[]
  /** @모두 */
  mentionAll?: boolean
}

/** 답장한 원래 메시지 (지웠으면 content 없이 deleted) */
export type ReplyPreview = { id: number; sender: ChatUser | null; content: string | null; type: ChatMessage['type']; fileCount: number; deleted: boolean }

/** 이모지 하나에 단 사람들 */
export type Reaction = { emoji: string; count: number; userIds: number[] }

/** 방 사람이 어디까지 읽었는지 */
export type ReadState = { userId: number; lastReadMessageId: number | null }

/** 방 공지 (방 위쪽에 고정) */
export type ChatNotice = { content: string; by: ChatUser | null; at: string | null }

export type ChatRoom = {
  id: number
  teamId: number
  /** GROUP = 채널 */
  type: 'DIRECT' | 'GROUP'
  /** 채널이 들어 있는 카테고리(부서). null = 카테고리 없음 */
  categoryId: number | null
  /** 채널 음성방에 지금 들어와 있는 사람 (없으면 null) */
  voice?: { meetingId: number; participants: ChatUser[] } | null
  name: string | null
  createdBy: number
  members: ChatUser[]
  lastMessage: ChatMessage | null
  unreadCount: number
  lastMessageAt: string | null
  createdAt: string
  /** 방 공지. 없으면 null */
  notice?: ChatNotice | null
  /** 방 사람마다 마지막으로 읽은 메시지 (메시지 옆 안 읽은 사람 수) */
  reads?: ReadState[]
  /** 내가 이 방 알림을 껐는지 */
  muted?: boolean
}

/** 채널을 묶는 카테고리 = 부서 (예: 개발 연구소 → #백오피스, #프론트오피스) */
export type ChatCategory = { id: number; name: string; sortOrder: number }

/**
 * 서버 /user/queue/chat 이벤트. MESSAGE_UPDATED = 고치거나 지운 메시지 (같은 id 를 바꿔 끼운다),
 * ROOM = 방 목록 다시, CATEGORIES = 카테고리 다시
 */
export type ChatEvent = {
  type: 'MESSAGE' | 'MESSAGE_UPDATED' | 'ROOM' | 'CATEGORIES' | 'READ' | 'TYPING'
  teamId: number
  roomId: number
  message: ChatMessage | null
  /** READ: 누가 어디까지 읽었는지 */
  read?: ReadState | null
  /** TYPING: 입력 중인 사람 */
  typing?: ChatUser | null
}

/** 채널에 올라온 AI 회의록 카드 (SYSTEM 메시지, key = meetingNotes) */
export type MeetingNotesCard = { meetingId: number; title: string; summary: string[]; decisions: number; actions: number }

// ---- 서버에 보내는 값 ----
export type OpenDirectRequest = { userId: number }
export type CreateGroupRequest = { name: string; memberIds: number[]; categoryId: number | null }
/** 채널 이름 바꾸기 · 카테고리 옮기기 (moveCategory=true 면 categoryId 로, null = 카테고리 밖) */
export type UpdateChannelRequest = { name?: string; categoryId?: number | null; moveCategory?: boolean }
export type CategoryRequest = { name: string }
export type InviteMembersRequest = { userIds: number[] }
/** content 가 null 이면 공지 내리기 */
export type NoticeRequest = { content: string | null }
export type SendMessageRequest = { content: string; replyToId?: number | null; mentionIds?: number[]; mentionAll?: boolean }
/** 보낼 때 함께: 답장할 메시지 · @멘션한 사람 · @모두 */
export type SendOptions = Omit<SendMessageRequest, 'content'>
export type ReactionRequest = { emoji: string }
export type MuteRequest = { muted: boolean }
export type MarkReadRequest = { messageId: number }
/** count = 전체, teams = 워크스페이스마다 (안 읽은 게 있는 곳만) */
export type UnreadCountResponse = { count: number; teams?: { teamId: number; count: number }[] }
/** 화면에서 쓰는 모양: 전체 + 워크스페이스 id → 수 */
export type ChatUnread = { total: number; byTeam: Record<number, number> }
