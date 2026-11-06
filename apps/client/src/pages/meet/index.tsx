import { useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { useMeQuery } from '@/entities/user'
import { FullPageSpinner } from '@/shared/ui'
import { MeetingRoom } from '@/widgets/meeting-room'

/** 회의실: 앱 셸 없이 전체 화면 */
export function MeetPage() {
  const { t } = useTranslation()
  const id = Number(useParams().meetingId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()

  const exit = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['meetings'] })
    toast(t('room.ended'))
    navigate(`/app/meetings/${id}`, { replace: true })
  }, [queryClient, navigate, id, t])

  if (!me) return <FullPageSpinner />
  return <MeetingRoom meetingId={id} selfName={me.nickname} selfAvatar={me.profileImageUrl} onExit={exit} />
}
