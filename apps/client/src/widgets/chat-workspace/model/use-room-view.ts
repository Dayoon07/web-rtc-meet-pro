import { useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { chatApi, chatKeys, type ChatMessage, type ChatRoom, useChatCategoriesQuery } from '@/entities/chat'
import { hasTeamPermission, useTeamQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { useSetNotice } from '@/features/chat-room-actions'
import { useVoice, voiceSession } from '@/features/voice-chat'
import { toastError } from '@/shared/api'
import { useDialog } from '@/shared/lib/use-dialog'
import { useRoomMessages } from './use-room-messages'

export function useRoomView(room: ChatRoom) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const { data: team } = useTeamQuery(room.teamId)
  const { data: categories = [] } = useChatCategoriesQuery(room.teamId)
  const can = (p: Parameters<typeof hasTeamPermission>[1]) => hasTeamPermission(team?.team, p)

  const voice = useVoice()
  const inThisVoice = voice.roomId === room.id && (voice.status === 'connected' || voice.status === 'connecting')

  const canNotice = room.type === 'DIRECT' || can('CREATE_GROUP_ROOM')
  const setNotice = useSetNotice(room)
  const messages = useRoomMessages(room, me?.id)

  const [params, setParams] = useSearchParams()
  const threadId = Number(params.get('thread')) || null
  const setThread = useCallback(
    (id: number | null, replace = false) =>
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          if (id) next.set('thread', String(id))
          else next.delete('thread')
          return next
        },
        { replace },
      ),
    [setParams],
  )
  const replaceRoot = useCallback((id: number) => setThread(id, true), [setThread])

  return {
    room,
    myId: me?.id,
    category: categories.find((c) => c.id === room.categoryId),
    canNotice,
    canManageChannel: room.type === 'GROUP' && can('CREATE_GROUP_ROOM'),
    canModerate: can('DELETE_ANY_MESSAGE'),
    voice: {
      inThisVoice,
      connecting: voice.status === 'connecting' && voice.roomId === room.id,
      toggle: () => (inThisVoice ? voiceSession.leave() : void voiceSession.join(room)),
    },
    toggleMute: () =>
      chatApi.setMuted(room.id, !room.muted).then(() => queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) }), toastError),
    makeNotice: (m: ChatMessage) => {
      if (!m.content) return
      if (room.notice && !window.confirm(t('chat.notice.replaceConfirm'))) return
      setNotice.mutate(m.content.slice(0, 1000))
    },
    dialogs: {
      invite: useDialog(),
      settings: useDialog(),
      notice: useDialog(),
      reaction: useDialog<ChatMessage, string>((emoji, message) => message && messages.react(message, emoji)),
    },
    messages,
    thread: {
      openId: threadId,
      open: (m: ChatMessage) => setThread(m.threadRootId ?? m.id),
      close: () => setThread(null),
      replaceRoot,
    },
  }
}

export type RoomViewModel = ReturnType<typeof useRoomView>
