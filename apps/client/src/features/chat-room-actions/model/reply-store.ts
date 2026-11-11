import { useSyncExternalStore } from 'react'
import type { ChatMessage } from '@/entities/chat'

/**
 * 방마다 "지금 답장하려는 메시지". 메시지 옆 답장 버튼(위젯)과 입력창(이 기능)이 함께 쓴다.
 * props 로 주고받지 않게 작은 저장소로 둔다 (화면을 옮겨도 그 방의 답장 대상은 남는다)
 */
const targets = new Map<number, ChatMessage>()
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((l) => l())

/** 이 메시지에 답장하기 (입력창 위에 "○○님에게 답장" 이 뜬다) */
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
