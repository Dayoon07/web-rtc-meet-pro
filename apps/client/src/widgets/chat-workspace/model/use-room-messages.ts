import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { chatApi, chatKeys, type ChatMessage, type ChatRoom } from '@/entities/chat'
import { toast } from 'sonner'
import { toastError } from '@/shared/api'

const PAGE = 50
const JUMP_PAGES = 20

export function useRoomMessages(room: ChatRoom, myId: number | undefined) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [hasMore, setHasMore] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [highlightId, setHighlightId] = useState<number | null>(null)

  const { data: messages = [], isPending } = useQuery({
    queryKey: chatKeys.messages(room.id),
    queryFn: async () => {
      const page = await chatApi.messages(room.id)
      setHasMore(page.length === PAGE)
      return page
    },
  })

  const lastId = messages.at(-1)?.id
  useEffect(() => {
    if (!lastId) return
    void chatApi.read(room.id, lastId).then(() => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.unread })
      queryClient.setQueryData<ChatRoom[]>(chatKeys.rooms(room.teamId), (rooms) => rooms?.map((r) => (r.id === room.id ? { ...r, unreadCount: 0 } : r)))
    })
  }, [lastId, room.id, room.teamId, queryClient])

  const scroller = useRef<HTMLDivElement>(null)
  const stickToBottom = useRef(true)
  const followBottom = useCallback(() => {
    const el = scroller.current
    if (el && stickToBottom.current) el.scrollTop = el.scrollHeight
  }, [])
  useLayoutEffect(followBottom, [lastId, followBottom])

  const replace = (m: ChatMessage) => {
    queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(room.id), (cur = []) => cur.map((x) => (x.id === m.id ? m : x)))
    void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) })
  }

  const reveal = (id: number) => {
    setHighlightId(id)
    requestAnimationFrame(() => scroller.current?.querySelector(`[data-message-id="${id}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' }))
    window.setTimeout(() => setHighlightId((cur) => (cur === id ? null : cur)), 2500)
  }

  return {
    messages,
    highlightId,
    jumpTo: async (id: number) => {
      let list = queryClient.getQueryData<ChatMessage[]>(chatKeys.messages(room.id)) ?? messages
      for (let i = 0; i < JUMP_PAGES && !list.some((m) => m.id === id); i++) {
        const first = list[0]
        if (!first || first.id < id) break
        const older = await chatApi.messages(room.id, first.id)
        setHasMore(older.length === PAGE)
        if (!older.length) break
        list = [...older, ...list]
        queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(room.id), list)
      }
      if (!list.some((m) => m.id === id)) {
        toast.message(t('chat.search.tooOld'))
        return false
      }
      stickToBottom.current = false
      reveal(id)
      return true
    },
    react: (m: ChatMessage, emoji: string) => {
      chatApi.react(m.id, emoji).then(replace, toastError)
    },
    isPending,
    hasMore,
    loadingOlder,
    scroller,
    followBottom,
    onScroll: (el: HTMLDivElement) => {
      stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
    },
    loadOlder: async () => {
      const first = messages[0]
      if (!first) return
      setLoadingOlder(true)
      try {
        const older = await chatApi.messages(room.id, first.id)
        setHasMore(older.length === PAGE)
        queryClient.setQueryData<ChatMessage[]>(chatKeys.messages(room.id), (cur = []) => [...older, ...cur])
      } finally {
        setLoadingOlder(false)
      }
    },
    editingId,
    startEdit: (m: ChatMessage) => setEditingId(m.id),
    cancelEdit: () => setEditingId(null),
    saveEdit: async (m: ChatMessage, content: string) => {
      try {
        replace(await chatApi.edit(m.id, content))
        setEditingId(null)
      } catch (e) {
        toastError(e)
        throw e
      }
    },
    remove: (m: ChatMessage) => {
      if (!window.confirm(t(m.sender?.id === myId ? 'chat.deleteConfirm' : 'chat.deleteOthersConfirm'))) return
      chatApi.remove(m.id).then(replace, toastError)
    },
  }
}

export type RoomMessagesModel = ReturnType<typeof useRoomMessages>
