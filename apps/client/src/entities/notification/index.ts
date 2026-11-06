import { useQuery } from '@tanstack/react-query'
import type { TFunction } from 'i18next'
import { http } from '@/shared/api'

export type NotificationType =
  | 'TEAM_INVITATION'
  | 'TEAM_INVITATION_ACCEPTED'
  | 'CHAT_ROOM_INVITED'
  | 'MEETING_SCHEDULED'
  | 'MEETING_REMINDER'
  | 'MEETING_STARTED'
  | 'MEETING_CANCELED'

export type AppNotification = {
  id: number
  type: NotificationType
  params: Record<string, string | number | null>
  link: string | null
  read: boolean
  createdAt: string
}

export const notificationKeys = { list: ['notifications'] as const, unread: ['notifications', 'unread'] as const }

export const notificationApi = {
  list: () => http<AppNotification[]>('/notifications'),
  unreadCount: () => http<{ count: number }>('/notifications/unread-count'),
  read: (id: number) => http<void>(`/notifications/${id}/read`, { method: 'POST' }),
  readAll: () => http<void>('/notifications/read-all', { method: 'POST' }),
}

export const useNotificationsQuery = (enabled: boolean) =>
  useQuery({ queryKey: notificationKeys.list, queryFn: notificationApi.list, enabled })
export const useUnreadNotificationsQuery = () =>
  useQuery({ queryKey: notificationKeys.unread, queryFn: notificationApi.unreadCount, select: (d) => d.count })

/** 서버는 type + params 만 보낸다 → 사용자 언어로 문장 생성 (시간은 사용자 시간대로) */
export function notificationText(n: AppNotification, t: TFunction, formatTime: (iso: string) => string) {
  const p = n.params
  const time = typeof p.scheduledAt === 'string' ? formatTime(p.scheduledAt) : ''
  return t(`notifications.types.${n.type}`, { ...p, time })
}
