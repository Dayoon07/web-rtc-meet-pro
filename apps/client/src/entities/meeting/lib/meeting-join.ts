import type { Meeting } from '../model/types'

const EARLY_JOIN_MS = 10 * 60 * 1000

export function canJoinNow(m: Meeting, myId: number | undefined, now = Date.now()) {
  if (m.status === 'LIVE') return true
  if (m.status === 'ENDED') return !m.chatRoomId && now < new Date(m.scheduledAt).getTime() + m.durationMinutes * 60_000
  if (m.status !== 'SCHEDULED') return false
  return m.host?.id === myId || now >= new Date(m.scheduledAt).getTime() - EARLY_JOIN_MS
}

export const joinOpensAt = (m: Meeting) => new Date(new Date(m.scheduledAt).getTime() - EARLY_JOIN_MS)
