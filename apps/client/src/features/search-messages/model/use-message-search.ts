import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { chatApi } from '@/entities/chat'

export function useMessageSearch(teamId: number, open: boolean) {
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')
  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(query.trim()), 300)
    return () => window.clearTimeout(id)
  }, [query])
  useEffect(() => {
    if (!open) {
      setQuery('')
      setDebounced('')
    }
  }, [open])

  const result = useQuery({
    queryKey: ['chat', 'search', teamId, debounced],
    queryFn: () => chatApi.search(teamId, debounced),
    enabled: open && teamId > 0 && debounced.length > 0,
    placeholderData: keepPreviousData,
    staleTime: 10_000,
  })
  return { query, setQuery, searched: debounced, results: debounced ? (result.data ?? []) : [], loading: result.isFetching }
}
