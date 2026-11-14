import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { useMeQuery } from '@/entities/user'
import { voiceSession } from '@/features/voice-chat'
import { FullPageSpinner } from '@/shared/ui'
import { MeetingRoom } from '@/widgets/meeting-room'
import { ROUTES } from '@/shared/config/routes'

export function MeetPage() {
  const { t } = useTranslation()
  const id = Number(useParams().meetingId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const [params] = useSearchParams()
  const voice = params.get('voice') === '1'
  const back = params.get('back')
  const backTo = back && back.startsWith(`${ROUTES.app}/`) ? back : null
  useState(() => voiceSession.leave())

  const exit = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['meetings'] })
    void queryClient.invalidateQueries({ queryKey: ['chat'] })
    toast(t(voice ? 'room.leftVoice' : 'room.ended'))
    navigate(backTo ?? ROUTES.meeting(id), { replace: true })
  }, [queryClient, navigate, id, t, voice, backTo])

  if (!me) return <FullPageSpinner />
  return <MeetingRoom entry={{ meetingId: id, selfName: me.nickname, selfAvatar: me.profileImageUrl, cameraOff: voice }} onExit={exit} />
}
