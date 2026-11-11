import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { chatApi, chatKeys, type ChatMessage, type ChatRoom } from '@/entities/chat'
import { toastError } from '@/shared/api'

const PAGE = 50

/**
 * 한 방의 메시지: 처음 50개 + "이전 메시지 더 보기", 읽음 처리, 고치기·지우기, 맨 아래 따라가기.
 * myId 는 내 메시지 판단·지우기 확인 문구에 쓴다
 */
export function useRoomMessages(room: ChatRoom, myId: number | undefined) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [hasMore, setHasMore] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)

  const { data: messages = [], isPending } = useQuery({
    queryKey: chatKeys.messages(room.id),
    queryFn: async () => {
      const page = await chatApi.messages(room.id)
      setHasMore(page.length === PAGE)
      return page
    },
  })

  // 마지막 메시지까지 읽음 처리 → 방 목록 배지·사이드바 배지 갱신
  const lastId = messages.at(-1)?.id
  useEffect(() => {
    if (!lastId) return
    void chatApi.read(room.id, lastId).then(() => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.unread })
      queryClient.setQueryData<ChatRoom[]>(chatKeys.rooms(room.teamId), (rooms) => rooms?.map((r) => (r.id === room.id ? { ...r, unreadCount: 0 } : r)))
    })
  }, [lastId, room.id, room.teamId, queryClient])

  // 새 메시지가 오면 맨 아래에 있을 때만 따라 내려간다 (위로 올려 읽는 중이면 그대로)
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

  return {
    messages,
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
