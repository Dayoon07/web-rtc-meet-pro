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
}

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
}

/** 채널을 묶는 카테고리 = 부서 (예: 개발 연구소 → #백오피스, #프론트오피스) */
export type ChatCategory = { id: number; name: string; sortOrder: number }

/**
 * 서버 /user/queue/chat 이벤트. MESSAGE_UPDATED = 고치거나 지운 메시지 (같은 id 를 바꿔 끼운다),
 * ROOM = 방 목록 다시, CATEGORIES = 카테고리 다시
 */
export type ChatEvent = { type: 'MESSAGE' | 'MESSAGE_UPDATED' | 'ROOM' | 'CATEGORIES'; teamId: number; roomId: number; message: ChatMessage | null }

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
export type SendMessageRequest = { content: string }
export type MarkReadRequest = { messageId: number }
export type UnreadCountResponse = { count: number }
