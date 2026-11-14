import type { TFunction } from 'i18next'
import type { ChatFile, ChatMessage, ChatRoom, MeetingNotesCard } from '../model/types'

export function roomTitle(room: ChatRoom, myId: number | undefined) {
  if (room.type === 'GROUP') return room.name ?? ''
  return room.members.find((m) => m.id !== myId)?.nickname ?? room.members[0]?.nickname ?? ''
}

export const roomPeer = (room: ChatRoom, myId: number | undefined) =>
  room.type === 'DIRECT' ? room.members.find((m) => m.id !== myId) ?? null : null

export function filesSummary(files: ChatFile[], t: TFunction): string {
  const n = files.length
  if (files.every((f) => f.image)) return n === 1 ? t('chat.file.photo') : t('chat.file.photos', { count: n })
  return n === 1 ? t('chat.file.one') : t('chat.file.many', { count: n })
}

export function meetingNotesCard(message: ChatMessage): MeetingNotesCard | null {
  if (message.type !== 'SYSTEM' || !message.content) return null
  try {
    const c = JSON.parse(message.content) as Partial<MeetingNotesCard> & { key?: string }
    if (c.key !== 'meetingNotes' || typeof c.meetingId !== 'number') return null
    return { meetingId: c.meetingId, title: c.title ?? '', summary: c.summary ?? [], decisions: c.decisions ?? 0, actions: c.actions ?? 0 }
  } catch {
    return null
  }
}

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
      case 'noticeSet':
        return t('chat.system.noticeSet', { actor })
      case 'noticeCleared':
        return t('chat.system.noticeCleared', { actor })
      case 'meetingNotes':
        return t('chat.system.meetingNotes', { title: meetingNotesCard(message)?.title ?? '' })
      default:
        return ''
    }
  } catch {
    return message.content ?? ''
  }
}

export function previewText(message: ChatMessage | null, t: TFunction, list: (items: string[]) => string) {
  if (!message) return ''
  if (message.deleted) return t('chat.deletedMessage')
  if (message.type === 'SYSTEM') return systemText(message, t, list)
  if (message.type === 'FILE') return message.content || filesSummary(message.files, t)
  return message.content ?? ''
}
