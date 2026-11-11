import type { TFunction } from 'i18next'
import type { ChatCategory, ChatRoom } from '@/entities/chat'

/** 방 목록의 한 묶음: 카테고리(부서) / 카테고리 없는 채널 / 1:1 대화 */
export type RoomSectionData = {
  key: string
  title: string
  rooms: ChatRoom[]
  /** 카테고리 묶음이면 그 카테고리 (+ · 이름 바꾸기 · 지우기 버튼) */
  category?: ChatCategory
  /** 채널을 더할 수 있는 묶음이면 넣을 카테고리 id (카테고리 밖이면 null) */
  addTo?: number | null
  /** 비었을 때 안내 문장 */
  emptyHint?: string
}

/** 디스코드처럼: 카테고리마다 채널, 카테고리 없는 채널, 1:1 대화 순서 */
export function buildSections(rooms: ChatRoom[], categories: ChatCategory[], canManage: boolean, t: TFunction): RoomSectionData[] {
  const channels = rooms.filter((r) => r.type === 'GROUP')
  const known = new Set(categories.map((c) => c.id))
  const loose = channels.filter((r) => r.categoryId == null || !known.has(r.categoryId))
  const sections: RoomSectionData[] = categories.map((c) => ({
    key: `c${c.id}`,
    title: c.name,
    rooms: channels.filter((r) => r.categoryId === c.id),
    category: c,
    addTo: canManage ? c.id : undefined,
    emptyHint: t('chat.emptyCategory'),
  }))
  if (loose.length > 0 || canManage) {
    sections.push({
      key: 'loose',
      title: t('chat.channels'),
      rooms: loose,
      addTo: canManage ? null : undefined,
      emptyHint: canManage ? t('chat.noChannels') : undefined,
    })
  }
  sections.push({ key: 'direct', title: t('chat.directs'), rooms: rooms.filter((r) => r.type === 'DIRECT'), emptyHint: t('chat.noDirects') })
  return sections
}

/** 말풍선 모양: 내 글(보라) / 남의 글(흰색) */
export const bubbleClass = (mine: boolean) =>
  [
    'rounded-2xl px-3.5 py-2 text-[15px] leading-relaxed break-words whitespace-pre-wrap',
    mine
      ? 'rounded-br-md bg-brand-600 text-white dark:bg-brand-500'
      : 'rounded-bl-md bg-white text-gray-900 shadow-xs ring-1 ring-gray-200 dark:bg-gray-800 dark:text-gray-100 dark:ring-gray-700',
  ].join(' ')
