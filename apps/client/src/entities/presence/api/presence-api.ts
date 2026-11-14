import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useMeQuery } from '@/entities/user'
import { API, realtime, request } from '@/shared/api'
import type { PresenceRequest, PresenceStatus, PresenceView } from '../model/types'

export const presenceKeys = { all: ['presence'] as const, team: (teamId: number) => ['presence', teamId] as const }

export const presenceApi = {
  team: (teamId: number) => request<PresenceView[]>(API.teams.presence(teamId)),
  setAway: (away: boolean) => request<void>(API.presence, { method: 'PUT', json: { away } satisfies PresenceRequest }),
}

export function usePresence(teamId: number | undefined) {
  const { data: me } = useMeQuery()
  const { data } = useQuery({
    queryKey: presenceKeys.team(teamId ?? 0),
    queryFn: () => presenceApi.team(teamId!),
    enabled: !!teamId,
    staleTime: 60_000,
  })
  const map = new Map((data ?? []).map((p) => [p.userId, p.status]))
  return (userId: number | undefined): PresenceStatus => {
    const status = (userId != null ? map.get(userId) : undefined) ?? 'OFFLINE'
    return userId === me?.id && status === 'OFFLINE' ? 'ONLINE' : status
  }
}

export function usePresenceRealtime() {
  const queryClient = useQueryClient()
  useEffect(() => realtime.onConnected(() => void queryClient.invalidateQueries({ queryKey: presenceKeys.all })), [queryClient])
  useEffect(
    () =>
      realtime.on('presence', (payload) => {
        const p = payload as PresenceView
        queryClient.setQueriesData<PresenceView[]>({ queryKey: presenceKeys.all }, (list) =>
          list ? [...list.filter((x) => x.userId !== p.userId), ...(p.status === 'OFFLINE' ? [] : [p])] : list,
        )
      }),
    [queryClient],
  )
}
