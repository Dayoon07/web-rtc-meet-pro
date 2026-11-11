import { useMutation, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { type Meeting, meetingApi, meetingKeys } from '@/entities/meeting'
import { toastError } from '@/shared/api'
import { Button } from '@/shared/ui'

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
