import { useSyncExternalStore } from 'react'
import type { ChatMessage } from '@/entities/chat'

const targets = new Map<number, ChatMessage>()
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((l) => l())

export function startReply(message: ChatMessage) {
  targets.set(message.roomId, message)
  emit()
}

export function clearReply(roomId: number) {
  if (targets.delete(roomId)) emit()
}

export function useReplyTarget(roomId: number): ChatMessage | null {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => targets.get(roomId) ?? null,
  )
}
