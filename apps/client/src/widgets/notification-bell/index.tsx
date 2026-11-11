import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Bell, BellRing, CalendarClock, CreditCard, Mail, MessageSquare, Sparkles, UserCheck, Users, Video, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { type AppNotification, notificationApi, notificationKeys, notificationText, type NotificationType, useNotificationsQuery, useUnreadNotificationsQuery } from '@/entities/notification'
import { useFormat } from '@/shared/lib/format'
import { cn } from '@/shared/lib/cn'
import { Menu, Spinner } from '@/shared/ui'

const ICONS: Record<NotificationType, typeof Bell> = {
  TEAM_INVITATION: Mail,
  TEAM_INVITATION_ACCEPTED: UserCheck,
  CHAT_ROOM_INVITED: MessageSquare,
  MEETING_SCHEDULED: CalendarClock,
  MEETING_REMINDER: BellRing,
  MEETING_STARTED: Video,
  MEETING_CANCELED: X,
  MEETING_UPDATED: CalendarClock,
  MEETING_NOTES_READY: Sparkles,
  PLAN_EXPIRING: CreditCard,
  PLAN_EXPIRED: CreditCard,
  PAYMENT_FAILED: CreditCard,
  MEMBER_OVERAGE: Users,
  OVERAGE_CHARGE_SOON: CreditCard,
}

/** 상단 바 종 아이콘: 안 읽은 개수 + 최근 알림 드롭다운 + 브라우저 알림 권한 */
export function NotificationBell() {
  const { t } = useTranslation()
  const fmt = useFormat()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [opened, setOpened] = useState(false)
  const { data: unread = 0 } = useUnreadNotificationsQuery()
  const { data: list, isPending } = useNotificationsQuery(opened)
  const [permission, setPermission] = useState(() => ('Notification' in window ? Notification.permission : 'denied'))

  const refresh = () => void queryClient.invalidateQueries({ queryKey: notificationKeys.list })
  const readAll = useMutation({
    mutationFn: notificationApi.readAll,
    onSuccess: () => {
      queryClient.setQueryData(notificationKeys.unread, { count: 0 })
      refresh()
    },
  })

  const openItem = (n: AppNotification, close: () => void) => {
    close()
    if (!n.read) {
      void notificationApi.read(n.id).then(() => {
        void queryClient.invalidateQueries({ queryKey: notificationKeys.unread })
        refresh()
      })
    }
    if (n.link) navigate(n.link)
  }

  return (
    <Menu
      label={t('notifications.title')}
      panelClassName="w-[22rem] max-w-[calc(100vw-2rem)] overflow-hidden"
      trigger={({ open, toggle, id }) => (
        <button
          type="button"
          onClick={() => {
            if (!open) setOpened(true)
            toggle()
          }}
          aria-expanded={open}
          aria-controls={id}
          aria-label={t('notifications.open')}
          title={t('notifications.title')}
          className="relative inline-flex size-9 items-center justify-center rounded-lg text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-white"
        >
          <Bell className="size-4.5" />
          {unread > 0 && (
            <span className="absolute top-1 right-1 min-w-4 rounded-full bg-red-500 px-1 text-center text-[10px] leading-4 font-bold text-white ring-2 ring-white dark:ring-gray-950">
              {unread > 9 ? '9+' : unread}
            </span>
          )}
        </button>
      )}
    >
      {(close) => (
        <div>
          <div className="flex items-center justify-between border-b border-gray-100 px-4 py-3 dark:border-gray-800">
            <p className="font-semibold text-gray-900 dark:text-white">{t('notifications.title')}</p>
            {unread > 0 && (
              <button type="button" onClick={() => readAll.mutate()} className="text-xs font-semibold text-brand-700 hover:underline dark:text-brand-300">
                {t('notifications.markAllRead')}
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto p-1.5">
            {isPending ? (
              <div className="flex justify-center py-8">
                <Spinner className="size-5 text-gray-400" />
              </div>
            ) : !list?.length ? (
              <p className="py-10 text-center text-sm text-gray-400">{t('notifications.empty')}</p>
            ) : (
              list.map((n) => {
                // 서버에 새 알림 종류가 생겨도 화면이 깨지지 않게 기본 아이콘
                const Icon = ICONS[n.type] ?? Bell
                return (
                  <button
                    key={n.id}
                    type="button"
                    onClick={() => openItem(n, close)}
                    className="flex w-full gap-3 rounded-lg px-3 py-2.5 text-left transition-colors hover:bg-gray-50 dark:hover:bg-gray-800"
                  >
                    <span
                      className={cn(
                        'mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full',
                        n.read ? 'bg-gray-100 text-gray-400 dark:bg-gray-800' : 'bg-brand-100 text-brand-700 dark:bg-brand-500/20 dark:text-brand-200',
                      )}
                    >
                      <Icon className="size-4" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className={cn('block text-sm', n.read ? 'text-gray-500 dark:text-gray-400' : 'font-medium text-gray-900 dark:text-white')}>
                        {notificationText(n, t, fmt.dateTime)}
                      </span>
                      <span className="mt-0.5 block text-xs text-gray-400">{fmt.relative(n.createdAt)}</span>
                    </span>
                    {!n.read && <span className="mt-2 size-2 shrink-0 rounded-full bg-brand-500" />}
                  </button>
                )
              })
            )}
          </div>
          {permission === 'default' && (
            <div className="border-t border-gray-100 p-2 dark:border-gray-800">
              <button
                type="button"
                onClick={() => void Notification.requestPermission().then(setPermission)}
                className="flex w-full items-center justify-center gap-2 rounded-lg py-2 text-sm font-medium text-brand-700 hover:bg-brand-50 dark:text-brand-300 dark:hover:bg-brand-500/10"
              >
                <BellRing className="size-4" />
                {t('notifications.enableBrowser')}
              </button>
            </div>
          )}
          {permission === 'denied' && 'Notification' in window && (
            <p className="border-t border-gray-100 px-4 py-2 text-center text-xs text-gray-400 dark:border-gray-800">{t('notifications.browserBlocked')}</p>
          )}
        </div>
      )}
    </Menu>
  )
}

/** 실시간 알림이 오면 목록·배지를 갱신하고, 허용된 경우 OS 알림도 띄운다 (app 레이어에서 한 번 사용) */
export function useNotificationSideEffects(subscribe: (cb: (payload: unknown) => void) => () => void) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  useEffect(
    () =>
      subscribe((payload) => {
        const n = payload as AppNotification
        void queryClient.invalidateQueries({ queryKey: notificationKeys.unread })
        void queryClient.invalidateQueries({ queryKey: notificationKeys.list })
        if (n.type.startsWith('MEETING')) void queryClient.invalidateQueries({ queryKey: ['meetings'] })
        if (n.type === 'TEAM_INVITATION') void queryClient.invalidateQueries({ queryKey: ['invitations'] })
        if (n.type === 'TEAM_INVITATION_ACCEPTED') void queryClient.invalidateQueries({ queryKey: ['teams'] })
        if (n.type.startsWith('PLAN') || n.type === 'PAYMENT_FAILED') void queryClient.invalidateQueries({ queryKey: ['subscriptions'] })

        const text = notificationText(n, t, fmt.dateTime)
        if ('Notification' in window && Notification.permission === 'granted' && document.visibilityState !== 'visible') {
          const os = new Notification('MQ', { body: text, tag: `wrmp-${n.id}`, icon: '/favicon.svg' })
          os.onclick = () => {
            window.focus()
            if (n.link) navigate(n.link)
          }
        }
      }),
    [subscribe, queryClient, t, fmt, navigate],
  )
}
