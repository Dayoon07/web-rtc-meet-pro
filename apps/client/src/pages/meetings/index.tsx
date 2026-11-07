import { CalendarClock, CalendarPlus, Video } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { type Meeting, MeetingStatusBadge, useMeetingsQuery } from '@/entities/meeting'
import { useMeQuery } from '@/entities/user'
import { CancelMeetingButton, JoinMeetingButton } from '@/features/meeting-actions'
import { ScheduleMeetingModal } from '@/features/schedule-meeting'
import { cn } from '@/shared/lib/cn'
import { intlLocale } from '@/shared/i18n'
import { useFormat } from '@/shared/lib/format'
import { Avatar, Button, EmptyState, Spinner } from '@/shared/ui'

function MeetingRow({ meeting, myId }: { meeting: Meeting; myId?: number }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const start = new Date(meeting.scheduledAt)
  return (
    <li className="flex flex-wrap items-center gap-4 rounded-2xl border border-gray-200 bg-white p-4 sm:flex-nowrap dark:border-gray-800 dark:bg-gray-900">
      {/* 달력 아이콘처럼 날짜 표시 */}
      <div className="flex w-16 shrink-0 flex-col items-center overflow-hidden rounded-xl border border-gray-200 dark:border-gray-700">
        <span className="w-full bg-brand-600 py-0.5 text-center text-[10px] font-bold text-white uppercase">
          {start.toLocaleDateString(intlLocale(), { month: 'short' })}
        </span>
        <span className="py-1 text-xl font-bold text-gray-900 dark:text-white">{start.getDate()}</span>
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <Link to={`/app/meetings/${meeting.id}`} className="truncate font-semibold text-gray-900 hover:underline dark:text-white">
            {meeting.title}
          </Link>
          <MeetingStatusBadge status={meeting.status} />
        </div>
        <p className="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
          {fmt.time(meeting.scheduledAt)} · {t('common.minutes', { count: meeting.durationMinutes })} · {meeting.teamName}
        </p>
        <div className="mt-2 flex items-center gap-2">
          <div className="flex -space-x-1.5">
            {meeting.invitees.slice(0, 5).map((u) => (
              <Avatar key={u.id} src={u.profileImageUrl} name={u.nickname} size="xs" ring />
            ))}
          </div>
          <span className="text-xs text-gray-500">
            {meeting.status === 'LIVE' ? t('meetings.participantsLive', { count: meeting.liveParticipants }) : t('meetings.invitees', { count: meeting.invitees.length })}
          </span>
        </div>
      </div>
      <div className="flex items-center gap-2">
        {meeting.canCancel && <CancelMeetingButton meeting={meeting} />}
        <JoinMeetingButton meeting={meeting} myId={myId} size="md" />
      </div>
    </li>
  )
}

export function MeetingsPage() {
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  const [scope, setScope] = useState<'upcoming' | 'past'>('upcoming')
  const { data: meetings, isPending } = useMeetingsQuery(scope)
  const [modal, setModal] = useState<'now' | 'schedule' | null>(null)

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('meetings.title')}</h2>
          <p className="mt-1 text-gray-500 dark:text-gray-400">{t('meetings.subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" icon={<CalendarPlus className="size-4" />} onClick={() => setModal('schedule')}>
            {t('meetings.schedule')}
          </Button>
          <Button icon={<Video className="size-4" />} onClick={() => setModal('now')}>
            {t('meetings.startNow')}
          </Button>
        </div>
      </div>

      <div role="tablist" className="inline-flex w-fit rounded-lg bg-gray-100 p-1 dark:bg-gray-800">
        {(['upcoming', 'past'] as const).map((key) => (
          <button
            key={key}
            role="tab"
            type="button"
            aria-selected={scope === key}
            onClick={() => setScope(key)}
            className={cn(
              'rounded-md px-4 py-1.5 text-sm font-medium transition',
              scope === key ? 'bg-white text-gray-900 shadow-sm dark:bg-gray-950 dark:text-white' : 'text-gray-500 hover:text-gray-800 dark:hover:text-gray-200',
            )}
          >
            {t(key === 'upcoming' ? 'meetings.upcoming' : 'meetings.past')}
          </button>
        ))}
      </div>

      {isPending ? (
        <Spinner className="mx-auto size-6 text-brand-600" />
      ) : !meetings?.length ? (
        <EmptyState
          icon={<CalendarClock />}
          title={t(scope === 'upcoming' ? 'meetings.empty' : 'meetings.pastEmpty')}
          action={scope === 'upcoming' ? <Button onClick={() => setModal('schedule')}>{t('meetings.schedule')}</Button> : undefined}
        />
      ) : (
        <ul className="flex flex-col gap-3">
          {meetings.map((m) => (
            <MeetingRow key={m.id} meeting={m} myId={me?.id} />
          ))}
        </ul>
      )}

      <ScheduleMeetingModal mode="now" open={modal === 'now'} onClose={() => setModal(null)} />
      <ScheduleMeetingModal mode="schedule" open={modal === 'schedule'} onClose={() => setModal(null)} />
    </div>
  )
}
