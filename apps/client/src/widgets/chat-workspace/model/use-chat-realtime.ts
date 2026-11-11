import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect } from 'react'
import { chatKeys, type ChatEvent, type ChatMessage, type ChatRoom } from '@/entities/chat'
import { realtime } from '@/shared/api'

/** 서버 /user/queue/chat 이벤트를 받아 방 목록·카테고리·메시지 캐시를 고친다 */
export function useChatRealtime() {
  const queryClient = useQueryClient()
  const onEvent = useCallback(
    (payload: unknown) => {
      const ev = payload as ChatEvent
      if (ev.type === 'CATEGORIES') {
        void queryClient.invalidateQueries({ queryKey: chatKeys.categories(ev.teamId) })
        return
      }
      if (ev.type === 'READ') {
        // 누가 어디까지 읽었는지만 바꾼다 (메시지 옆 안 읽은 사람 수가 줄어든다)
        const read = ev.read
        if (!read) return
        queryClient.setQueryData<ChatRoom[]>(chatKeys.rooms(ev.teamId), (rooms) =>
          rooms?.map((r) =>
            r.id !== ev.roomId ? r : { ...r, reads: [...(r.reads ?? []).filter((x) => x.userId !== read.userId), read] },
          ),
        )
        return
      }
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(ev.teamId) })
      const msg = ev.message
      if (msg && ev.type === 'MESSAGE_UPDATED') {
        // 누가 고치거나 지운 메시지: 같은 id 를 바꿔 끼운다
        queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(ev.roomId), (cur) => cur?.map((m) => (m.id === msg.id ? msg : m)))
      } else if (msg) {
        queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(ev.roomId), (cur) => (cur && !cur.some((m) => m.id === msg.id) ? [...cur, msg] : cur))
      }
    },
    [queryClient],
  )
  useEffect(() => realtime.on('chat', onEvent), [onEvent])
}
