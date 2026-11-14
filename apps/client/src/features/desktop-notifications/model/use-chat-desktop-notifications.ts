import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { chatKeys, type ChatEvent, type ChatRoom } from '@/entities/chat'
import { type Me, userKeys } from '@/entities/user'
import { realtime } from '@/shared/api'
import { ROUTES } from '@/shared/config/routes'
import { inQuietHours, showDesktopNotification } from '@/shared/lib/desktop-notify'

export function useChatDesktopNotifications() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  useEffect(
    () =>
      realtime.on('chat', (payload) => {
        const ev = payload as ChatEvent
        const m = ev.message
        if (ev.type !== 'MESSAGE' || !m?.sender || m.threadRootId) return
        const me = queryClient.getQueryData<Me>(userKeys.me)
        if (!me || m.sender.id === me.id) return
        if (m.mentionAll || m.mentions?.includes(me.id)) return
        if (inQuietHours(me.dndStart, me.dndEnd, me.dndTimeZone)) return
        const room = queryClient.getQueryData<ChatRoom[]>(chatKeys.rooms(ev.teamId))?.find((r) => r.id === ev.roomId)
        if (room?.muted) return

        const text = m.content?.replace(/\s+/g, ' ').trim().slice(0, 140) || t('notifications.desktop.file')
        const group = room?.type === 'GROUP'
        void showDesktopNotification(
          {
            title: group ? `# ${room?.name ?? ''}` : m.sender.nickname,
            body: group ? `${m.sender.nickname}: ${text}` : text,
            tag: `room-${ev.roomId}`,
            link: ROUTES.chat(ev.teamId, ev.roomId),
          },
          (link) => navigate(link),
        )
      }),
    [queryClient, navigate, t],
  )
}
