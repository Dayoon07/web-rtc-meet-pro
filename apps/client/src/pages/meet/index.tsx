import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { useMeQuery } from '@/entities/user'
import { voiceSession } from '@/features/voice-chat'
import { FullPageSpinner } from '@/shared/ui'
import { MeetingRoom } from '@/widgets/meeting-room'

/** 회의실: 앱 셸 없이 전체 화면 */
export function MeetPage() {
  const { t } = useTranslation()
  const id = Number(useParams().meetingId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const [params] = useSearchParams()
  // 채널 음성방: ?voice=1 이면 카메라를 끈 채로 들어가고, 나오면 그 채널로 (back 은 앱 안 주소만)
  const voice = params.get('voice') === '1'
  const back = params.get('back')
  const backTo = back && back.startsWith('/app/') ? back : null
  // 채팅에서 음성으로 들어가 있었으면 먼저 끊는다 (같은 사람이 두 번 들어가지 않게). 회의실이 접속하기 전에 한 번
  useState(() => voiceSession.leave())

  const exit = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['meetings'] })
    void queryClient.invalidateQueries({ queryKey: ['chat'] })
    toast(t(voice ? 'room.leftVoice' : 'room.ended'))
    navigate(backTo ?? `/app/meetings/${id}`, { replace: true })
  }, [queryClient, navigate, id, t, voice, backTo])

  if (!me) return <FullPageSpinner />
  return <MeetingRoom meetingId={id} selfName={me.nickname} selfAvatar={me.profileImageUrl} onExit={exit} cameraOff={voice} />
}
