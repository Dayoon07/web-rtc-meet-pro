import { useQuery } from '@tanstack/react-query'
import { useEffect, useLayoutEffect, useRef } from 'react'
import { chatApi, chatKeys } from '@/entities/chat'

/**
 * 스레드 창 하나의 자료: 시작 글 + 답글 (실시간으로 오는 답글·고친 글은 use-chat-realtime 이 이 캐시에 넣는다).
 * 주소의 thread 가 답글 id 였으면 서버가 시작 글을 돌려주니, 시작 글 id 로 주소를 바꾼다 (onRoot).
 * 새 답글이 붙으면 맨 아래로 내려간다
 */
export function useThread(rootId: number, onRoot: (rootId: number) => void) {
  const query = useQuery({ queryKey: chatKeys.thread(rootId), queryFn: () => chatApi.thread(rootId), retry: false })
  const realRoot = query.data?.root.id
  useEffect(() => {
    if (realRoot && realRoot !== rootId) onRoot(realRoot)
  }, [realRoot, rootId, onRoot])

  const scroller = useRef<HTMLDivElement>(null)
  const count = query.data?.replies.length ?? 0
  useLayoutEffect(() => {
    const el = scroller.current
    if (el) el.scrollTop = el.scrollHeight
  }, [count, query.isSuccess])

  return { thread: query.data, isPending: query.isPending, isError: query.isError, scroller }
}
