import { useEffect, useState, useSyncExternalStore } from 'react'
import type { ChatUser } from '@/entities/chat'

/** "입력 중" 이벤트를 받은 뒤 이만큼 지나면 지운다 (보내는 쪽은 3초마다 다시 알린다) */
const TTL_MS = 5000

/** 방마다 지금 입력 중인 사람 (userId → 사람, 언제까지) */
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
  /** 그 사람이 메시지를 보내면 바로 지운다 */
  remove(roomId: number, userId: number) {
    if (typing.get(roomId)?.delete(userId)) emit()
  },
}

/** 이 방에서 지금 입력 중인 사람들 (시간이 지나면 저절로 사라진다) */
export function useTypingUsers(roomId: number): ChatUser[] {
  useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => version,
  )
  // 1초마다 다시 그려서 시간이 지난 사람을 뺀다
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
