import { useQuery } from '@tanstack/react-query'
import { useEffect, useLayoutEffect, useRef } from 'react'
import { chatApi, chatKeys } from '@/entities/chat'

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
