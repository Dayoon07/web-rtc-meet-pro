import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { chatKeys, type ChatEvent, type ChatRoom } from '@/entities/chat'
import { type Me, userKeys } from '@/entities/user'
import { realtime } from '@/shared/api'
import { ROUTES } from '@/shared/config/routes'
import { inQuietHours, showDesktopNotification } from '@/shared/lib/desktop-notify'

/**
 * 새 채팅 메시지 → 크롬(브라우저) 알림. MQ 탭을 안 보고 있을 때만 (shouldNotifyDesktop).
 * 서버 웹 푸시와 같은 규칙: 내 메시지·알림 끈 방·방해 금지 시간은 빼고, 나를 부른 멘션은 알림(CHAT_MENTION)으로 따로 뜬다.
 * 같은 방은 tag(room-방번호)로 하나만 남긴다. 앱 레이어(RealtimeBridge)에서 한 번 쓴다
 */
export function useChatDesktopNotifications() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  useEffect(
    () =>
      realtime.on('chat', (payload) => {
        const ev = payload as ChatEvent
        const m = ev.message
        // 스레드 답글은 스레드 알림(THREAD_REPLY)으로 따로 뜬다
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
