import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { chatApi, chatKeys, type ChatMessage, type ChatRoom, useChatCategoriesQuery } from '@/entities/chat'
import { hasTeamPermission, useTeamQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { useSetNotice } from '@/features/chat-room-actions'
import { useVoice, voiceSession } from '@/features/voice-chat'
import { toastError } from '@/shared/api'
import { useDialog } from '@/shared/lib/use-dialog'
import { useRoomMessages } from './use-room-messages'

/**
 * 열린 방 하나의 상태: 권한(공지·채널 설정·남의 메시지 지우기), 채널 음성, 창들(초대·설정·공지), 메시지.
 * 방 화면 조각들은 이 값을 RoomContext 로 받아 쓴다
 */
export function useRoomView(room: ChatRoom) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const { data: team } = useTeamQuery(room.teamId)
  const { data: categories = [] } = useChatCategoriesQuery(room.teamId)
  const can = (p: Parameters<typeof hasTeamPermission>[1]) => hasTeamPermission(team?.team, p)

  // 채널 음성: 화면을 옮기지 않고 여기서 바로 말한다 (사이드바 아래 "음성 연결됨" 막대)
  const voice = useVoice()
  const inThisVoice = voice.roomId === room.id && (voice.status === 'connected' || voice.status === 'connecting')

  // 공지: 채널은 워크스페이스 OWNER·ADMIN, 1:1 방은 두 사람 모두
  const canNotice = room.type === 'DIRECT' || can('CREATE_GROUP_ROOM')
  const setNotice = useSetNotice(room)
  const messages = useRoomMessages(room, me?.id)

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
    /** 이 방 알림 끄기·켜기 (나만) */
    toggleMute: () =>
      chatApi.setMuted(room.id, !room.muted).then(() => queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) }), toastError),
    /** 글 메시지를 공지로 (지금 공지가 있으면 바꿀지 묻는다) */
    makeNotice: (m: ChatMessage) => {
      if (!m.content) return
      if (room.notice && !window.confirm(t('chat.notice.replaceConfirm'))) return
      setNotice.mutate(m.content.slice(0, 1000))
    },
    dialogs: {
      invite: useDialog(),
      settings: useDialog(),
      notice: useDialog(),
      /** 다른 이모지로 반응 (data = 반응할 메시지) */
      reaction: useDialog<ChatMessage, string>((emoji, message) => message && messages.react(message, emoji)),
    },
    messages,
  }
}

export type RoomViewModel = ReturnType<typeof useRoomView>
