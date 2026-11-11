import { useQuery } from '@tanstack/react-query'
import { API, request } from '@/shared/api'
import type { AppNotification, UnreadCountResponse } from '../model/types'

export const notificationKeys = { list: ['notifications'] as const, unread: ['notifications', 'unread'] as const }

export const notificationApi = {
  list: () => request<AppNotification[]>(API.notifications.list),
  unreadCount: () => request<UnreadCountResponse>(API.notifications.unreadCount),
  read: (id: number) => request<void>(API.notifications.read(id), { method: 'POST' }),
  readAll: () => request<void>(API.notifications.readAll, { method: 'POST' }),
}

export const useNotificationsQuery = (enabled: boolean) => useQuery({ queryKey: notificationKeys.list, queryFn: notificationApi.list, enabled })
export const useUnreadNotificationsQuery = () =>
  useQuery({ queryKey: notificationKeys.unread, queryFn: notificationApi.unreadCount, select: (d) => d.count })
