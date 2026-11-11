import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { chatApi, chatKeys, type ChatMessage, type ChatRoom } from '@/entities/chat'
import { toast } from 'sonner'
import { toastError } from '@/shared/api'

const PAGE = 50
/** 메시지로 갈 때 이전 메시지를 최대 몇 번 더 불러올지 */
const JUMP_PAGES = 20

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
  /** 잠깐 반짝이는 메시지 (검색·답장 원문으로 갔을 때) */
  const [highlightId, setHighlightId] = useState<number | null>(null)

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

  /** 그 메시지를 화면 가운데로 + 잠깐 반짝 */
  const reveal = (id: number) => {
    setHighlightId(id)
    requestAnimationFrame(() => scroller.current?.querySelector(`[data-message-id="${id}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' }))
    window.setTimeout(() => setHighlightId((cur) => (cur === id ? null : cur)), 2500)
  }

  return {
    messages,
    highlightId,
    /**
     * 메시지로 가기 (검색 결과·답장 원문). 아직 안 불러온 옛 메시지면 이전 메시지를 이어서 불러온다 (최대 20번 = 1,000개).
     * 찾으면 true
     */
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
    /** 이모지 반응 달기·떼기 */
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
