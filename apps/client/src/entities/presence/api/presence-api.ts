import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useMeQuery } from '@/entities/user'
import { API, realtime, request } from '@/shared/api'
import type { PresenceRequest, PresenceStatus, PresenceView } from '../model/types'

export const presenceKeys = { all: ['presence'] as const, team: (teamId: number) => ['presence', teamId] as const }

export const presenceApi = {
  /** 워크스페이스에서 지금 접속 중·자리 비움인 사람 (나머지는 접속 안 함) */
  team: (teamId: number) => request<PresenceView[]>(API.teams.presence(teamId)),
  setAway: (away: boolean) => request<void>(API.presence, { method: 'PUT', json: { away } satisfies PresenceRequest }),
}

/** 워크스페이스 사람들의 접속 상태 (userId → 상태). 없는 사람은 접속 안 함. 나는 이 화면을 보고 있으니 최소한 접속 중 */
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

/** 실시간 접속 상태 이벤트를 받아 불러 둔 워크스페이스 목록들을 고친다 (앱에서 한 번) */
export function usePresenceRealtime() {
  const queryClient = useQueryClient()
  // (다시) 연결되면 놓친 상태를 다시 불러온다 (서버가 연결을 기록한 뒤라 내 상태도 맞게 나온다)
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
