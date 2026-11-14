import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CalendarPlus, MessageSquare, Video } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import { chatApi } from '@/entities/chat'
import { MeetingStatusBadge, useMeetingsQuery } from '@/entities/meeting'
import { hasTeamPermission, teamApi, teamKeys, type TeamRole, useTeamQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { InviteLinkPanel, InviteMemberPanel } from '@/features/invite-member'
import { JoinMeetingButton } from '@/features/meeting-actions'
import { ScheduleMeetingModal, type ScheduleTarget } from '@/features/schedule-meeting'
import { RemoveMemberButton, TeamExitButton } from '@/features/team-membership'
import { toastError } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { useFormat } from '@/shared/lib/format'
import { Avatar, Badge, Button, Card, EmptyState, Select, Spinner } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { useDialog } from '@/shared/lib/use-dialog'
import { PresenceDot, usePresence } from '@/entities/presence'

export function TeamDetailPage() {
  const { t } = useTranslation()
  const fmt = useFormat()
  const navigate = useNavigate()
  const teamId = Number(useParams().teamId)
  const { data: me } = useMeQuery()
  const { data: detail, isPending, isError } = useTeamQuery(teamId)
  const statusOf = usePresence(teamId)
  const { data: upcoming = [] } = useMeetingsQuery('upcoming')
  const [tab, setTab] = useState<'members' | 'invite' | 'meetings'>('members')
  const scheduling = useDialog<ScheduleTarget>()

  const queryClient = useQueryClient()
  const changeRole = useMutation({
    mutationFn: ({ userId, role }: { userId: number; role: Exclude<TeamRole, 'OWNER'> }) => teamApi.changeRole(teamId, userId, role),
    onSuccess: (m) => {
      void queryClient.invalidateQueries({ queryKey: teamKeys.detail(teamId) })
      toast.success(t('teams.members.roleChanged', { name: m.nickname, role: t(`teams.roles.${m.role}`) }))
    },
    onError: (e) => toastError(e),
  })

  const openDirect = useMutation({
    mutationFn: (userId: number) => chatApi.openDirect(teamId, userId),
    onSuccess: (room) => navigate(ROUTES.chat(teamId, room.id)),
    onError: (e) => toastError(e),
  })

  if (isPending) return <Spinner className="mx-auto size-6 text-brand-600" />
  if (isError || !detail) return <EmptyState icon={<Video />} title={t('teams.notFound')} />

  const { team, members } = detail
  const canManageRoles = hasTeamPermission(team, 'MANAGE_ROLES')
  const canInvite = hasTeamPermission(team, 'INVITE_MEMBER')
  const meetings = upcoming.filter((m) => m.teamId === teamId)

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div className="flex flex-wrap items-center gap-4">
        <span className="flex size-14 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-400 to-brand-600 text-2xl font-bold text-white">
          {team.name.slice(0, 1).toUpperCase()}
        </span>
        <div className="min-w-0 flex-1 basis-56">
          <div className="flex items-center gap-2">
            <h2 className="truncate text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{team.name}</h2>
            <Badge tone={team.myRole === 'OWNER' ? 'brand' : 'gray'}>{t(`teams.roles.${team.myRole}`)}</Badge>
          </div>
          <p className="mt-0.5 text-sm text-gray-500 dark:text-gray-400">{team.description ?? t('teams.memberCount', { count: team.memberCount })}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <TeamExitButton team={team} />
          <Button variant="outline" icon={<MessageSquare className="size-4" />} onClick={() => navigate(ROUTES.chat(teamId))}>
            {t('nav.chat')}
          </Button>
          <Button variant="outline" icon={<CalendarPlus className="size-4" />} onClick={() => scheduling.show({ mode: 'schedule', teamId })}>
            {t('meetings.schedule')}
          </Button>
          <Button icon={<Video className="size-4" />} onClick={() => scheduling.show({ mode: 'now', teamId })}>
            {t('meetings.startNow')}
          </Button>
        </div>
      </div>

      <div role="tablist" className="flex gap-1 border-b border-gray-200 dark:border-gray-800">
        {(canInvite ? (['members', 'invite', 'meetings'] as const) : (['members', 'meetings'] as const)).map((key) => (
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

      {tab === 'invite' && canInvite ? (
        <div className="grid gap-6 xl:grid-cols-2">
          <Card>
            <h3 className="mb-4 font-semibold text-gray-900 dark:text-white">{t('teams.members.invite.title')}</h3>
            <InviteMemberPanel detail={detail} />
          </Card>
          <Card>
            <h3 className="mb-4 font-semibold text-gray-900 dark:text-white">{t('teams.inviteLink.title')}</h3>
            <InviteLinkPanel teamId={teamId} />
          </Card>
        </div>
      ) : tab === 'members' ? (
        <div>
          <Card className="p-0">
            <ul className="divide-y divide-gray-100 dark:divide-gray-800">
              {members.map((m) => (
                <li key={m.userId} className="flex items-center gap-3 px-5 py-3.5">
                  <span className="relative shrink-0">
                    <Avatar src={m.profileImageUrl} name={m.nickname} size="md" />
                    <PresenceDot status={statusOf(m.userId)} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-gray-900 dark:text-white">
                      {m.nickname}
                      {m.userId === me?.id && <span className="ml-1.5 text-xs text-gray-400">({t('common.you')})</span>}
                    </p>
                    <p className="truncate text-xs text-gray-500">{m.email}</p>
                  </div>
                  {canManageRoles && m.role !== 'OWNER' && m.userId !== me?.id ? (
                    <Select
                      aria-label={t('teams.members.roleLabel', { name: m.nickname })}
                      value={m.role}
                      disabled={changeRole.isPending}
                      onChange={(e) => changeRole.mutate({ userId: m.userId, role: e.target.value as Exclude<TeamRole, 'OWNER'> })}
                      compact
                      className="shrink-0"
                    >
                      <option value="ADMIN">{t('teams.roles.ADMIN')}</option>
                      <option value="MEMBER">{t('teams.roles.MEMBER')}</option>
                    </Select>
                  ) : (
                    <Badge tone={m.role === 'OWNER' ? 'brand' : 'gray'}>{t(`teams.roles.${m.role}`)}</Badge>
                  )}
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
                  <RemoveMemberButton teamId={teamId} member={m} />
                </li>
              ))}
            </ul>
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
              <Link to={ROUTES.meeting(m.id)} className="min-w-0 flex-1 truncate font-medium text-gray-900 hover:underline dark:text-white">
                {m.title}
              </Link>
              <MeetingStatusBadge status={m.status} />
              <JoinMeetingButton meeting={m} />
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState icon={<CalendarPlus />} title={t('meetings.empty')} description={t('meetings.subtitle')} />
      )}

            {scheduling.open && <ScheduleMeetingModal dialog={scheduling} />}
    </div>
  )
}
