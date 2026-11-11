import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { chatApi, chatKeys, type ChatRoom } from '@/entities/chat'
import { toastError } from '@/shared/api'

/** 공지 올리기·고치기·내리기 (서버에 저장하고 방 목록을 다시 받는다) */
export function useSetNotice(room: ChatRoom) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (content: string | null) => chatApi.setNotice(room.id, content),
    onSuccess: (_, content) => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) })
      toast.success(t(content ? 'chat.notice.posted' : 'chat.notice.removed'))
    },
    onError: (e) => toastError(e),
  })
}
