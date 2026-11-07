import { ArrowLeft, CalendarClock, Pencil, StickyNote, Users } from 'lucide-react'
import { type ReactNode, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { MeetingStatusBadge, useMeetingQuery } from '@/entities/meeting'
import { useMeQuery } from '@/entities/user'
import { CancelMeetingButton, JoinMeetingButton } from '@/features/meeting-actions'
import { MeetingNotesCard } from '@/features/meeting-notes'
import { RecordingList } from '@/features/meeting-recorder/RecordingList'
import { ScheduleMeetingModal } from '@/features/schedule-meeting'
import { useFormat } from '@/shared/lib/format'
import { Avatar, Button, Card, EmptyState, Spinner } from '@/shared/ui'

function Row({ icon, label, children }: { icon: ReactNode; label: string; children: ReactNode }) {
  return (
    <div className="flex gap-4 py-3">
      <span className="flex w-28 shrink-0 items-center gap-2 text-sm text-gray-500 dark:text-gray-400 [&_svg]:size-4">
        {icon}
        {label}
      </span>
      <div className="min-w-0 flex-1 text-sm text-gray-900 dark:text-gray-100">{children}</div>
    </div>
  )
}

export function MeetingDetailPage() {
  const { t } = useTranslation()
  const fmt = useFormat()
  const id = Number(useParams().meetingId)
  const { data: me } = useMeQuery()
  const { data: meeting, isPending, isError } = useMeetingQuery(id)
  const [editing, setEditing] = useState(false)

  if (isPending) return <Spinner className="mx-auto size-6 text-brand-600" />
  if (isError || !meeting) return <EmptyState icon={<CalendarClock />} title={t('errors.MEETING_NOT_FOUND')} />

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <Link to="/app/meetings" className="inline-flex w-fit items-center gap-1.5 text-sm text-gray-500 hover:text-gray-900 dark:hover:text-white">
        <ArrowLeft className="size-4" />
        {t('meetings.detail.back')}
      </Link>
      <Card className="flex flex-col gap-2">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <MeetingStatusBadge status={meeting.status} />
            <h2 className="mt-2 text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{meeting.title}</h2>
            {meeting.host && <p className="mt-1 text-sm text-gray-500">{t('meetings.host', { name: meeting.host.nickname })}</p>}
          </div>
          <div className="flex items-center gap-2">
            {meeting.canEdit && (
              <>
                <Button variant="outline" icon={<Pencil className="size-4" />} onClick={() => setEditing(true)}>
                  {t('meetings.edit')}
                </Button>
                <ScheduleMeetingModal mode="edit" meeting={meeting} open={editing} onClose={() => setEditing(false)} />
              </>
            )}
            {meeting.canCancel && <CancelMeetingButton meeting={meeting} />}
            <JoinMeetingButton meeting={meeting} myId={me?.id} size="md" />
          </div>
        </div>
        <div className="mt-4 divide-y divide-gray-100 dark:divide-gray-800">
          <Row icon={<CalendarClock />} label={t('meetings.detail.when')}>
            {fmt.dateTime(meeting.scheduledAt)} · {t('common.minutes', { count: meeting.durationMinutes })}
          </Row>
          <Row icon={<Users />} label={t('meetings.detail.team')}>
            <Link to={`/app/teams/${meeting.teamId}`} className="hover:underline">
              {meeting.teamName}
            </Link>
          </Row>
          <Row icon={<Users />} label={t('meetings.form.invitees')}>
            <div className="flex flex-wrap gap-2">
              {meeting.invitees.map((u) => (
                <span key={u.id} className="flex items-center gap-1.5 rounded-full bg-gray-100 py-0.5 pr-2.5 pl-0.5 dark:bg-gray-800">
                  <Avatar src={u.profileImageUrl} name={u.nickname} size="xs" />
                  {u.nickname}
                </span>
              ))}
            </div>
          </Row>
          {meeting.description && (
            <Row icon={<StickyNote />} label={t('meetings.detail.notes')}>
              <p className="whitespace-pre-wrap">{meeting.description}</p>
            </Row>
          )}
        </div>
      </Card>

      <MeetingNotesCard meeting={meeting} />
      <RecordingList meeting={meeting} />
    </div>
  )
}
