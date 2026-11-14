import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect } from 'react'
import { chatKeys, type ChatEvent, type ChatMessage, type ChatRoom, type ChatThread } from '@/entities/chat'
import { realtime } from '@/shared/api'
import { typingStore } from './typing-store'

export function useChatRealtime() {
  const queryClient = useQueryClient()
  const onEvent = useCallback(
    (payload: unknown) => {
      const ev = payload as ChatEvent
      if (ev.type === 'CATEGORIES') {
        void queryClient.invalidateQueries({ queryKey: chatKeys.categories(ev.teamId) })
        return
      }
      if (ev.type === 'TYPING') {
        if (ev.typing) typingStore.add(ev.roomId, ev.typing)
        return
      }
      if (ev.type === 'READ') {
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
      if (msg?.sender && ev.type === 'MESSAGE') typingStore.remove(ev.roomId, msg.sender.id)
      if (msg && ev.type === 'MESSAGE_UPDATED') {
        queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(ev.roomId), (cur) => cur?.map((m) => (m.id === msg.id ? msg : m)))
        queryClient.setQueryData<ChatThread>(chatKeys.thread(msg.threadRootId ?? msg.id), (cur) =>
          !cur ? cur : msg.id === cur.root.id ? { ...cur, root: msg } : { ...cur, replies: cur.replies.map((m) => (m.id === msg.id ? msg : m)) },
        )
      } else if (msg?.threadRootId) {
        queryClient.setQueryData<ChatThread>(chatKeys.thread(msg.threadRootId), (cur) =>
          cur && !cur.replies.some((m) => m.id === msg.id) ? { ...cur, replies: [...cur.replies, msg] } : cur,
        )
      } else if (msg) {
        queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(ev.roomId), (cur) => (cur && !cur.some((m) => m.id === msg.id) ? [...cur, msg] : cur))
      }
    },
    [queryClient],
  )
  useEffect(() => realtime.on('chat', onEvent), [onEvent])
}
