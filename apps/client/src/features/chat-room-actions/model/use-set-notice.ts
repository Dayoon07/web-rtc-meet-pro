import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { chatApi, chatKeys, type ChatRoom } from '@/entities/chat'
import { toastError } from '@/shared/api'

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
