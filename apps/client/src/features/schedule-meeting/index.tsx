import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { meetingApi, meetingKeys } from '@/entities/meeting'
import { useTeamQuery, useTeamsQuery } from '@/entities/team'
import { errorMessage } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { Avatar, Button, Checkbox, Field, FieldError, Input, Modal, Select } from '@/shared/ui'

const DURATIONS = [15, 30, 45, 60, 90, 120]

const pad = (n: number) => String(n).padStart(2, '0')
/** 다음 정각 또는 30분 (예: 10:12 → 10:30) */
function nextSlot() {
  const d = new Date()
  d.setMinutes(d.getMinutes() < 30 ? 30 : 60, 0, 0)
  return { date: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`, time: `${pad(d.getHours())}:${pad(d.getMinutes())}` }
}

/**
 * mode=now: 지금 회의 시작 → 만들고 바로 회의실로
 * mode=schedule: 날짜·시간·참석자 지정 → 시작 10분 전 알림
 */
export function ScheduleMeetingModal({
  mode,
  open,
  onClose,
  defaultTeamId,
}: {
  mode: 'now' | 'schedule'
  open: boolean
  onClose: () => void
  defaultTeamId?: number
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: teams } = useTeamsQuery(open)
  const [teamId, setTeamId] = useState<number>(defaultTeamId ?? 0)
  const { data: team } = useTeamQuery(open ? teamId : 0)
  const [title, setTitle] = useState('')
  const [slot, setSlot] = useState(nextSlot)
  const [duration, setDuration] = useState(60)
  const [description, setDescription] = useState('')
  const [everyone, setEveryone] = useState(true)
  const [invitees, setInvitees] = useState<Set<number>>(new Set())

  useEffect(() => {
    if (!teamId && teams?.length) setTeamId(defaultTeamId ?? teams[0].id)
  }, [teams, teamId, defaultTeamId])

  useEffect(() => {
    if (open) setSlot(nextSlot())
  }, [open])

  const schedule = useMutation({
    mutationFn: () =>
      meetingApi.schedule(teamId, {
        title: title.trim(),
        description: description.trim() || undefined,
        // 사용자 시간대 → 오프셋 포함 ISO (서버가 자기 시간대로 변환)
        scheduledAt: mode === 'schedule' ? new Date(`${slot.date}T${slot.time}:00`).toISOString() : undefined,
        durationMinutes: duration,
        inviteeIds: everyone ? [] : [...invitees],
      }),
    onSuccess: (meeting) => {
      void queryClient.invalidateQueries({ queryKey: meetingKeys.upcoming })
      onClose()
      setTitle('')
      setDescription('')
      if (mode === 'now') navigate(`/meet/${meeting.id}`)
      else toast.success(t('meetings.form.scheduled', { title: meeting.title }))
    },
  })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (teamId && title.trim()) schedule.mutate()
  }

  const others = team?.members ?? []

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t(mode === 'now' ? 'meetings.form.titleNew' : 'meetings.form.titleSchedule')}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="meeting-form" loading={schedule.isPending} disabled={!teamId || !title.trim()}>
            {t(mode === 'now' ? 'meetings.form.submitNow' : 'meetings.form.submitSchedule')}
          </Button>
        </>
      }
    >
      {teams && !teams.length ? (
        <p className="py-6 text-center text-sm text-gray-500">{t('meetings.form.noTeams')}</p>
      ) : (
        <form id="meeting-form" onSubmit={onSubmit} className="flex flex-col gap-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t('meetings.form.team')} htmlFor="m-team" required>
              <Select id="m-team" value={teamId} onChange={(e) => setTeamId(Number(e.target.value))}>
                {teams?.map((tm) => (
                  <option key={tm.id} value={tm.id}>
                    {tm.name}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label={t('meetings.form.name')} htmlFor="m-title" required>
              <Input id="m-title" autoFocus maxLength={100} placeholder={t('meetings.form.namePlaceholder')} value={title} onChange={(e) => setTitle(e.target.value)} />
            </Field>
          </div>

          {mode === 'schedule' && (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <Field label={t('meetings.form.date')} htmlFor="m-date" required>
                <Input id="m-date" type="date" value={slot.date} onChange={(e) => setSlot({ ...slot, date: e.target.value })} />
              </Field>
              <Field label={t('meetings.form.time')} htmlFor="m-time" required>
                <Input id="m-time" type="time" step={300} value={slot.time} onChange={(e) => setSlot({ ...slot, time: e.target.value })} />
              </Field>
              <Field label={t('meetings.form.duration')} htmlFor="m-dur" className="col-span-2 sm:col-span-1">
                <Select id="m-dur" value={duration} onChange={(e) => setDuration(Number(e.target.value))}>
                  {DURATIONS.map((d) => (
                    <option key={d} value={d}>
                      {t('common.minutes', { count: d })}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
          )}

          <div>
            <p className="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">{t('meetings.form.invitees')}</p>
            <Checkbox label={t('meetings.form.allMembers')} checked={everyone} onChange={(e) => setEveryone(e.target.checked)} />
            {!everyone && (
              <div className="mt-3 flex flex-wrap gap-2">
                {others.map((m) => {
                  const on = invitees.has(m.userId)
                  return (
                    <button
                      key={m.userId}
                      type="button"
                      onClick={() =>
                        setInvitees((s) => {
                          const next = new Set(s)
                          if (on) next.delete(m.userId)
                          else next.add(m.userId)
                          return next
                        })
                      }
                      className={cn(
                        'flex items-center gap-2 rounded-full border py-1 pr-3 pl-1 text-sm transition',
                        on
                          ? 'border-brand-500 bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-200'
                          : 'border-gray-200 text-gray-600 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800',
                      )}
                    >
                      <Avatar src={m.profileImageUrl} name={m.nickname} size="xs" />
                      {m.nickname}
                    </button>
                  )
                })}
              </div>
            )}
          </div>

          <Field label={t('meetings.form.description')} htmlFor="m-desc" hint={t('common.optional')}>
            <Input id="m-desc" maxLength={1000} placeholder={t('meetings.form.descriptionPlaceholder')} value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>

          {mode === 'schedule' && (
            <p className="flex items-center gap-2 rounded-lg bg-brand-50 px-3 py-2.5 text-sm text-brand-800 dark:bg-brand-500/10 dark:text-brand-200">
              <Bell className="size-4 shrink-0" />
              {t('meetings.form.reminderNote')}
            </p>
          )}
          <FieldError>{errorMessage(schedule.error)}</FieldError>
        </form>
      )}
    </Modal>
  )
}
