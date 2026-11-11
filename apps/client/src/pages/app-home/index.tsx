import { CalendarClock, CalendarPlus, MessageSquare, Users, Video } from 'lucide-react'
import { type ReactNode, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { MeetingStatusBadge, useMeetingsQuery } from '@/entities/meeting'
import { planName } from '@/entities/plan'
import { useTeamsQuery } from '@/entities/team'
import { useMeQuery } from '@/entities/user'
import { CreateTeamModal } from '@/features/create-team'
import { JoinMeetingButton } from '@/features/meeting-actions'
import { ScheduleMeetingModal, type ScheduleTarget } from '@/features/schedule-meeting'
import { cn } from '@/shared/lib/cn'
import { isSameDay, useFormat } from '@/shared/lib/format'
import { Card, EmptyState } from '@/shared/ui'
import { ROUTES } from '@/shared/config/routes'
import { useDialog } from '@/shared/lib/use-dialog'

/** Zoom 홈 화면처럼 큰 아이콘 타일로 주요 동작을 바로 보이게 */
/** tile = 아이콘 · 이름 · 설명 · 색 */
function ActionTile({ tile, onClick }: { tile: { icon: ReactNode; label: string; hint: string; tone: 'brand' | 'blue' | 'gray' }; onClick: () => void }) {
  const { icon, label, hint, tone } = tile
  return (
    <button
      type="button"
      onClick={onClick}
      className="group flex flex-col items-center gap-3 rounded-2xl border border-gray-200 bg-white p-5 text-center transition hover:-translate-y-0.5 hover:shadow-md dark:border-gray-800 dark:bg-gray-900"
    >
      <span
        className={cn(
          'flex size-14 items-center justify-center rounded-2xl text-white shadow-sm transition group-hover:scale-105 [&_svg]:size-6',
          tone === 'brand' && 'bg-brand-600 shadow-brand-600/30',
          tone === 'blue' && 'bg-sky-500 shadow-sky-500/30',
          tone === 'gray' && 'bg-gray-700',
        )}
      >
        {icon}
      </span>
      <span>
        <span className="block font-semibold text-gray-900 dark:text-white">{label}</span>
        <span className="mt-0.5 block text-xs text-gray-500 dark:text-gray-400">{hint}</span>
      </span>
    </button>
  )
}

function Clock({ todayCount }: { todayCount: number }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 30_000)
    return () => clearInterval(timer)
  }, [])
  return (
    <div className="rounded-2xl bg-gradient-to-br from-brand-600 to-brand-800 p-6 text-white">
      <p className="text-4xl font-bold tabular-nums">{fmt.time(now)}</p>
      <p className="mt-1 text-sm text-brand-100">{fmt.longDate(now)}</p>
      <div className="mt-6 flex items-center gap-2 rounded-xl bg-white/10 px-3 py-2.5 text-sm">
        <CalendarClock className="size-4 shrink-0" />
        {todayCount ? t('dashboard.meetingsToday', { count: todayCount }) : t('dashboard.noMeetingsToday')}
      </div>
    </div>
  )
}

export function AppHomePage() {
  const { t } = useTranslation()
  const fmt = useFormat()
  const navigate = useNavigate()
  const { data: me } = useMeQuery()
  const { data: teams } = useTeamsQuery()
  const plan = me?.plan
  const { data: upcoming = [] } = useMeetingsQuery('upcoming')
  const [creatingTeam, setCreatingTeam] = useState(false)
  const scheduling = useDialog<ScheduleTarget>()
  const today = upcoming.filter((m) => isSameDay(new Date(m.scheduledAt), new Date())).length

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-8">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{t('dashboard.greeting', { name: me?.nickname ?? '' })}</h2>
        <p className="mt-1 text-gray-500 dark:text-gray-400">{t('dashboard.subtitle')}</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          {/* 채팅 중심: 채팅 → 워크스페이스 → 회의 */}
          <ActionTile tile={{ icon: <MessageSquare />, label: t('dashboard.actions.chat'), hint: t('dashboard.actions.chatHint'), tone: 'brand' }} onClick={() => navigate(ROUTES.chat())} />
          <ActionTile tile={{ icon: <Users />, label: t('dashboard.actions.createTeam'), hint: t('dashboard.actions.createTeamHint', { count: teams?.length ?? 0 }), tone: 'blue' }} onClick={() => setCreatingTeam(true)} />
          <ActionTile tile={{ icon: <Video />, label: t('dashboard.actions.newMeeting'), hint: t('dashboard.actions.newMeetingHint'), tone: 'gray' }} onClick={() => scheduling.show({ mode: 'now' })} />
          <ActionTile tile={{ icon: <CalendarPlus />, label: t('dashboard.actions.schedule'), hint: t('dashboard.actions.scheduleHint'), tone: 'gray' }} onClick={() => scheduling.show({ mode: 'schedule' })} />
        </div>
        <Clock todayCount={today} />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <section className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <h3 className="font-semibold text-gray-900 dark:text-white">{t('dashboard.upcoming')}</h3>
            <Link to={ROUTES.meetings} className="text-sm font-semibold text-brand-700 hover:underline dark:text-brand-300">
              {t('dashboard.seeAll')}
            </Link>
          </div>
          {upcoming.length ? (
            <ul className="flex flex-col gap-2">
              {upcoming.slice(0, 5).map((m) => (
                <li key={m.id} className="flex items-center gap-4 rounded-xl border border-gray-200 bg-white px-4 py-3 dark:border-gray-800 dark:bg-gray-900">
                  <div className="w-16 shrink-0 text-center">
                    <p className="text-sm font-bold text-gray-900 tabular-nums dark:text-white">{fmt.time(m.scheduledAt)}</p>
                    <p className="text-[11px] text-gray-500">{fmt.monthDay(m.scheduledAt)}</p>
                  </div>
                  <Link to={ROUTES.meeting(m.id)} className="min-w-0 flex-1">
                    <p className="truncate font-medium text-gray-900 hover:underline dark:text-white">{m.title}</p>
                    <p className="truncate text-xs text-gray-500">{m.teamName}</p>
                  </Link>
                  <MeetingStatusBadge status={m.status} />
                  <JoinMeetingButton meeting={m} />
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState icon={<CalendarClock />} title={t('dashboard.upcomingEmpty')} description={t('dashboard.upcomingEmptyHint')} />
          )}
        </section>

        {plan && (
          <Card className="flex flex-col gap-5 p-5">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs text-gray-500 dark:text-gray-400">{t('dashboard.currentPlan')}</p>
                <p className="text-lg font-bold text-gray-900 dark:text-white">{planName(plan)}</p>
              </div>
              <Link to={ROUTES.billing} className="text-sm font-semibold text-brand-700 hover:underline dark:text-brand-300">
                {t('dashboard.change')}
              </Link>
            </div>
            <p className="text-xs text-gray-500 dark:text-gray-400">{t('dashboard.planPerWorkspace')}</p>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div className="rounded-lg bg-gray-50 p-3 dark:bg-gray-800/60">
                <p className="text-xs text-gray-500 dark:text-gray-400">{t('dashboard.membersPerTeam')}</p>
                <p className="font-semibold text-gray-900 dark:text-white">{t('common.people', { count: plan.maxMembersPerTeam })}</p>
              </div>
              <div className="rounded-lg bg-gray-50 p-3 dark:bg-gray-800/60">
                <p className="text-xs text-gray-500 dark:text-gray-400">{t('dashboard.participantsPerMeeting')}</p>
                <p className="font-semibold text-gray-900 dark:text-white">{t('common.people', { count: plan.maxMeetingParticipants })}</p>
              </div>
            </div>
          </Card>
        )}
      </div>

            {scheduling.open && <ScheduleMeetingModal dialog={scheduling} />}
      <CreateTeamModal open={creatingTeam} onClose={() => setCreatingTeam(false)} />
    </div>
  )
}
