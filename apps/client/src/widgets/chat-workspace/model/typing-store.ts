import { useEffect, useState, useSyncExternalStore } from 'react'
import type { ChatUser } from '@/entities/chat'

const TTL_MS = 5000

const typing = new Map<number, Map<number, { user: ChatUser; until: number }>>()
const listeners = new Set<() => void>()
let version = 0
const emit = () => {
  version++
  listeners.forEach((l) => l())
}

export const typingStore = {
  add(roomId: number, user: ChatUser) {
    const room = typing.get(roomId) ?? new Map()
    room.set(user.id, { user, until: Date.now() + TTL_MS })
    typing.set(roomId, room)
    emit()
  },
  remove(roomId: number, userId: number) {
    if (typing.get(roomId)?.delete(userId)) emit()
  },
}

export function useTypingUsers(roomId: number): ChatUser[] {
  useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => version,
  )
  const [now, setNow] = useState(() => Date.now())
  const room = typing.get(roomId)
  const active = room ? [...room.values()].filter((t) => t.until > now) : []
  useEffect(() => {
    if (!active.length) return
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [active.length])
  return active.map((t) => t.user)
}
