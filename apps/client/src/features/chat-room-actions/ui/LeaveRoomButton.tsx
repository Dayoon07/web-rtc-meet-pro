import { useMutation, useQueryClient } from '@tanstack/react-query'
import { LogOut } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { chatApi, chatKeys, type ChatRoom } from '@/entities/chat'
import { toastError } from '@/shared/api'
import { Button } from '@/shared/ui'

export function LeaveRoomButton({ room, onLeft }: { room: ChatRoom; onLeft: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const leave = useMutation({
    mutationFn: () => chatApi.leave(room.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: chatKeys.rooms(room.teamId) })
      onLeft()
    },
    onError: (e) => toastError(e),
  })
  return (
    <Button
      variant="ghost"
      size="sm"
      icon={<LogOut className="size-4" />}
      loading={leave.isPending}
      onClick={() => window.confirm(t('chat.leaveConfirm')) && leave.mutate()}
    >
      <span className="hidden lg:inline">{t('chat.leave')}</span>
    </Button>
  )
}
