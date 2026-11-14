import { Video } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { canJoinNow, joinOpensAt, type Meeting } from '@/entities/meeting'
import { useFormat } from '@/shared/lib/format'
import { LinkButton } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { useMeQuery } from '@/entities/user'

export function JoinMeetingButton({ meeting, size = 'sm' }: { meeting: Meeting; size?: 'sm' | 'md' }) {
  const { t } = useTranslation()
  const myId = useMeQuery().data?.id
  const fmt = useFormat()
  if (meeting.status === 'CANCELED') return null
  if (meeting.status === 'ENDED' && !canJoinNow(meeting, myId)) return null
  if (!canJoinNow(meeting, myId)) {
    return <span className="text-xs text-gray-500 dark:text-gray-400">{t('meetings.joinOpensAt', { time: fmt.time(joinOpensAt(meeting)) })}</span>
  }
  return (
    <LinkButton to={ROUTES.meet(meeting.id)} size={size} icon={<Video className="size-4" />} variant={meeting.status === 'LIVE' ? 'danger' : 'primary'}>
      {t('meetings.join')}
    </LinkButton>
  )
}
