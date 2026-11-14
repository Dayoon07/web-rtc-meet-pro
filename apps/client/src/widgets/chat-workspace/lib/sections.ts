import type { TFunction } from 'i18next'
import type { ChatCategory, ChatRoom } from '@/entities/chat'

export type RoomSectionData = {
  key: string
  title: string
  rooms: ChatRoom[]
  category?: ChatCategory
  addTo?: number | null
  emptyHint?: string
}

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

export const bubbleClass = (mine: boolean) =>
  [
    'rounded-2xl px-3.5 py-2 text-[15px] leading-relaxed break-words whitespace-pre-wrap',
    mine
      ? 'rounded-br-md bg-brand-600 text-white dark:bg-brand-500'
      : 'rounded-bl-md bg-white text-gray-900 shadow-xs ring-1 ring-gray-200 dark:bg-gray-800 dark:text-gray-100 dark:ring-gray-700',
  ].join(' ')
