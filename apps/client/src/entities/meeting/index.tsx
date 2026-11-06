import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { http } from '@/shared/api'
import { cn } from '@/shared/lib/cn'

export type MeetingStatus = 'SCHEDULED' | 'LIVE' | 'ENDED' | 'CANCELED'
export type MeetingUser = { id: number; nickname: string; profileImageUrl: string | null }

export type Meeting = {
  id: number
  teamId: number
  teamName: string
  title: string
  description: string | null
  status: MeetingStatus
  scheduledAt: string
  durationMinutes: number
  startedAt: string | null
  endedAt: string | null
  host: MeetingUser | null
  invitees: MeetingUser[]
  liveParticipants: number
}

export type JoinInfo = { meeting: Meeting; mediaUrl: string; token: string }

export type ScheduleBody = {
  title: string
  description?: string
  /** 비우면 지금 바로 시작 */
  scheduledAt?: string
  durationMinutes: number
  inviteeIds: number[]
}

export const meetingKeys = {
  upcoming: ['meetings', 'upcoming'] as const,
  past: ['meetings', 'past'] as const,
  detail: (id: number) => ['meetings', id] as const,
}

export const meetingApi = {
  list: (scope: 'upcoming' | 'past') => http<Meeting[]>(`/meetings?scope=${scope}`),
  detail: (id: number) => http<Meeting>(`/meetings/${id}`),
  schedule: (teamId: number, body: ScheduleBody) => http<Meeting>(`/teams/${teamId}/meetings`, { method: 'POST', json: body }),
  cancel: (id: number) => http<void>(`/meetings/${id}/cancel`, { method: 'POST' }),
  join: (id: number) => http<JoinInfo>(`/meetings/${id}/join`, { method: 'POST' }),
}

export const useMeetingsQuery = (scope: 'upcoming' | 'past') =>
  useQuery({ queryKey: scope === 'upcoming' ? meetingKeys.upcoming : meetingKeys.past, queryFn: () => meetingApi.list(scope) })

export const useMeetingQuery = (id: number) => useQuery({ queryKey: meetingKeys.detail(id), queryFn: () => meetingApi.detail(id) })

const EARLY_JOIN_MS = 10 * 60 * 1000

/** 서버 규칙과 같게: 진행 중이거나, 주최자이거나, 시작 10분 전부터 */
export function canJoinNow(m: Meeting, myId: number | undefined, now = Date.now()) {
  if (m.status === 'LIVE') return true
  // 모두 나가서 끝났어도 예정 시간 안이면 다시 열 수 있다 (서버 Meeting.canReopen 과 같은 규칙)
  if (m.status === 'ENDED') return now < new Date(m.scheduledAt).getTime() + m.durationMinutes * 60_000
  if (m.status !== 'SCHEDULED') return false
  return m.host?.id === myId || now >= new Date(m.scheduledAt).getTime() - EARLY_JOIN_MS
}

export const joinOpensAt = (m: Meeting) => new Date(new Date(m.scheduledAt).getTime() - EARLY_JOIN_MS)

export function MeetingStatusBadge({ status }: { status: MeetingStatus }) {
  const { t } = useTranslation()
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-semibold',
        status === 'LIVE' && 'bg-red-50 text-red-600 dark:bg-red-500/15 dark:text-red-300',
        status === 'SCHEDULED' && 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-200',
        (status === 'ENDED' || status === 'CANCELED') && 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
      )}
    >
      {status === 'LIVE' && <span className="size-1.5 animate-pulse rounded-full bg-red-500" />}
      {t(`meetings.status.${status}`)}
    </span>
  )
}
