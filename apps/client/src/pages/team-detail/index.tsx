import { useMutation } from '@tanstack/react-query'
import { CalendarPlus, MessageSquare, Video } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import { chatApi } from '@/entities/chat'
import { MeetingStatusBadge, useMeetingsQuery } from '@/entities/meeting'
import { useTeamQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { InviteMemberPanel } from '@/features/invite-member'
import { JoinMeetingButton } from '@/features/meeting-actions'
import { ScheduleMeetingModal } from '@/features/schedule-meeting'
import { toastError } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { useFormat } from '@/shared/lib/format'
import { Avatar, Badge, Button, Card, EmptyState, Spinner } from '@/shared/ui'

export function TeamDetailPage() {
  const { t } = useTranslation()
  const fmt = useFormat()
  const navigate = useNavigate()
  const teamId = Number(useParams().teamId)
  const { data: me } = useMeQuery()
  const { data: detail, isPending, isError } = useTeamQuery(teamId)
  const { data: upcoming = [] } = useMeetingsQuery('upcoming')
  const [tab, setTab] = useState<'members' | 'meetings'>('members')
  const [modal, setModal] = useState<'now' | 'schedule' | null>(null)

  const openDirect = useMutation({
    mutationFn: (userId: number) => chatApi.openDirect(teamId, userId),
    onSuccess: (room) => navigate(`/app/chat/${teamId}/${room.id}`),
    onError: (e) => toastError(e),
  })

  if (isPending) return <Spinner className="mx-auto size-6 text-brand-600" />
  if (isError || !detail) return <EmptyState icon={<Video />} title={t('teams.notFound')} />

  const { team, members } = detail
  const meetings = upcoming.filter((m) => m.teamId === teamId)

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div className="flex flex-wrap items-center gap-4">
        <span className="flex size-14 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-400 to-brand-600 text-2xl font-bold text-white">
          {team.name.slice(0, 1).toUpperCase()}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h2 className="truncate text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{team.name}</h2>
            <Badge tone={team.myRole === 'OWNER' ? 'brand' : 'gray'}>{t(`teams.roles.${team.myRole}`)}</Badge>
          </div>
          <p className="mt-0.5 text-sm text-gray-500 dark:text-gray-400">{team.description ?? t('teams.memberCount', { count: team.memberCount })}</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" icon={<MessageSquare className="size-4" />} onClick={() => navigate(`/app/chat/${teamId}`)}>
            {t('nav.chat')}
          </Button>
          <Button variant="outline" icon={<CalendarPlus className="size-4" />} onClick={() => setModal('schedule')}>
            {t('meetings.schedule')}
          </Button>
          <Button icon={<Video className="size-4" />} onClick={() => setModal('now')}>
            {t('meetings.startNow')}
          </Button>
        </div>
      </div>

      <div role="tablist" className="flex gap-1 border-b border-gray-200 dark:border-gray-800">
        {(['members', 'meetings'] as const).map((key) => (
          <button
            key={key}
            role="tab"
            type="button"
            aria-selected={tab === key}
            onClick={() => setTab(key)}
            className={cn(
              '-mb-px border-b-2 px-4 py-2.5 text-sm font-medium transition-colors',
              tab === key ? 'border-brand-600 text-brand-700 dark:text-brand-300' : 'border-transparent text-gray-500 hover:text-gray-800 dark:hover:text-gray-200',
            )}
          >
            {t(`teams.tabs.${key}`)}
            {key === 'members' && <span className="ml-1.5 text-gray-400">{members.length}</span>}
          </button>
        ))}
      </div>

      {tab === 'members' ? (
        <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
          <Card className="p-0">
            <ul className="divide-y divide-gray-100 dark:divide-gray-800">
              {members.map((m) => (
                <li key={m.userId} className="flex items-center gap-3 px-5 py-3.5">
                  <Avatar src={m.profileImageUrl} name={m.nickname} size="md" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-gray-900 dark:text-white">
                      {m.nickname}
                      {m.userId === me?.id && <span className="ml-1.5 text-xs text-gray-400">({t('common.you')})</span>}
                    </p>
                    <p className="truncate text-xs text-gray-500">{m.email}</p>
                  </div>
                  <Badge tone={m.role === 'OWNER' ? 'brand' : 'gray'}>{t(`teams.roles.${m.role}`)}</Badge>
                  {m.userId !== me?.id && (
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={<MessageSquare className="size-4" />}
                      loading={openDirect.isPending && openDirect.variables === m.userId}
                      onClick={() => openDirect.mutate(m.userId)}
                    >
                      <span className="hidden sm:inline">{t('teams.members.directChat')}</span>
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          </Card>
          <Card>
            <h3 className="mb-4 font-semibold text-gray-900 dark:text-white">{t('teams.members.invite.title')}</h3>
            <InviteMemberPanel detail={detail} />
          </Card>
        </div>
      ) : meetings.length ? (
        <ul className="flex flex-col gap-2">
          {meetings.map((m) => (
            <li key={m.id} className="flex items-center gap-4 rounded-xl border border-gray-200 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-900">
              <div className="w-24 shrink-0">
                <p className="text-sm font-bold text-gray-900 dark:text-white">{fmt.time(m.scheduledAt)}</p>
                <p className="text-xs text-gray-500">{fmt.date(m.scheduledAt)}</p>
              </div>
              <Link to={`/app/meetings/${m.id}`} className="min-w-0 flex-1 truncate font-medium text-gray-900 hover:underline dark:text-white">
                {m.title}
              </Link>
              <MeetingStatusBadge status={m.status} />
              <JoinMeetingButton meeting={m} myId={me?.id} />
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState icon={<CalendarPlus />} title={t('meetings.empty')} description={t('meetings.subtitle')} />
      )}

      <ScheduleMeetingModal mode="now" open={modal === 'now'} onClose={() => setModal(null)} defaultTeamId={teamId} />
      <ScheduleMeetingModal mode="schedule" open={modal === 'schedule'} onClose={() => setModal(null)} defaultTeamId={teamId} />
    </div>
  )
}
