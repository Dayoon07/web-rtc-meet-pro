import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Video, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { canJoinNow, joinOpensAt, type Meeting, meetingApi, meetingKeys } from '@/entities/meeting'
import { toastError } from '@/shared/api'
import { useFormat } from '@/shared/lib/format'
import { Button, LinkButton } from '@/shared/ui'

/** 참여 버튼: 입장 가능하면 /meet/:id 로, 아니면 "HH:MM 부터 입장 가능" */
export function JoinMeetingButton({ meeting, myId, size = 'sm' }: { meeting: Meeting; myId: number | undefined; size?: 'sm' | 'md' }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  if (meeting.status === 'CANCELED') return null
  if (meeting.status === 'ENDED' && !canJoinNow(meeting, myId)) return null
  if (!canJoinNow(meeting, myId)) {
    return <span className="text-xs text-gray-500 dark:text-gray-400">{t('meetings.joinOpensAt', { time: fmt.time(joinOpensAt(meeting)) })}</span>
  }
  return (
    <LinkButton to={`/meet/${meeting.id}`} size={size} icon={<Video className="size-4" />} variant={meeting.status === 'LIVE' ? 'danger' : 'primary'}>
      {t('meetings.join')}
    </LinkButton>
  )
}

export function CancelMeetingButton({ meeting, onCanceled }: { meeting: Meeting; onCanceled?: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const cancel = useMutation({
    mutationFn: () => meetingApi.cancel(meeting.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['meetings'] })
      void queryClient.invalidateQueries({ queryKey: meetingKeys.detail(meeting.id) })
      toast(t('meetings.canceled'))
      onCanceled?.()
    },
    onError: (e) => toastError(e),
  })
  if (meeting.status !== 'SCHEDULED') return null
  return (
    <Button
      variant="ghost"
      size="sm"
      icon={<X className="size-4" />}
      loading={cancel.isPending}
      onClick={() => window.confirm(t('meetings.cancelConfirm')) && cancel.mutate()}
    >
      {t('meetings.cancel')}
    </Button>
  )
}
