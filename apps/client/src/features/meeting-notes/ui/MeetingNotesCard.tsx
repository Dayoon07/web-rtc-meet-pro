import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarCheck, CheckCircle2, ChevronDown, ListChecks, RefreshCw, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { type Meeting, meetingApi, meetingKeys } from '@/entities/meeting'
import { ApiError, toastError } from '@/shared/api'
import { useFormat } from '@/shared/lib/format'
import { Badge, Button, Card, Spinner } from '@/shared/ui'

export function MeetingNotesCard({ meeting }: { meeting: Meeting }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const queryClient = useQueryClient()
  const [showTranscript, setShowTranscript] = useState(false)
  const { data: notes } = useQuery({
    queryKey: meetingKeys.notes(meeting.id),
    queryFn: () => meetingApi.notes(meeting.id),
    enabled: !!meeting.notesStatus,
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 2,
    refetchInterval: (q) => (['RECORDING', 'PAUSED', 'PROCESSING'].includes(q.state.data?.status ?? '') ? 5_000 : false),
  })
  const { data: lines, isFetching: loadingLines } = useQuery({
    queryKey: meetingKeys.transcript(meeting.id),
    queryFn: () => meetingApi.transcript(meeting.id),
    enabled: showTranscript,
  })
  const regenerate = useMutation({
    mutationFn: () => meetingApi.regenerateNotes(meeting.id),
    onSuccess: (n) => queryClient.setQueryData(meetingKeys.notes(meeting.id), n),
    onError: (e) => toastError(e),
  })

  if (!meeting.notesStatus || !notes) return null
  const busy = notes.status === 'PROCESSING'
  const live = notes.status === 'RECORDING' || notes.status === 'PAUSED'

  return (
    <Card className="p-0">
      <h3 className="flex items-center gap-2 border-b border-gray-100 px-5 py-3.5 font-semibold text-gray-900 dark:border-gray-800 dark:text-white">
        <Sparkles className="size-4 text-brand-600" />
        {t('notes.title')}
        <span className="ml-auto flex items-center gap-2 text-xs font-normal text-gray-500">
          {notes.generatedAt && fmt.dateTime(notes.generatedAt)}
          {notes.canRegenerate && (
            <Button size="sm" variant="ghost" icon={<RefreshCw className="size-4" />} loading={regenerate.isPending} onClick={() => regenerate.mutate()}>
              {t('notes.regenerate')}
            </Button>
          )}
        </span>
      </h3>

      <div className="flex flex-col gap-5 px-5 py-4">
        {live && <Badge tone="brand">{t('notes.statusLive')}</Badge>}
        {busy && (
          <p className="flex items-center gap-2 text-sm text-gray-500">
            <Spinner className="size-4 text-brand-600" />
            {t('notes.statusProcessing')}
          </p>
        )}
        {notes.status === 'EMPTY' && <p className="text-sm text-gray-500">{t('notes.statusEmpty')}</p>}
        {notes.status === 'FAILED' && <p className="text-sm text-red-600 dark:text-red-400">{t('notes.statusFailed')}</p>}

        {notes.status === 'READY' && (
          <>
            {notes.title && <p className="text-base font-semibold text-gray-900 dark:text-white">{notes.title}</p>}
            {notes.summary.length > 0 && (
              <ul className="flex list-disc flex-col gap-1.5 pl-5 text-sm text-gray-700 dark:text-gray-300">
                {notes.summary.map((s, i) => (
                  <li key={i}>{s}</li>
                ))}
              </ul>
            )}
            {notes.decisions.length > 0 && (
              <section>
                <h4 className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-gray-900 dark:text-white">
                  <CheckCircle2 className="size-4 text-emerald-600" />
                  {t('notes.decisions')}
                </h4>
                <ul className="flex flex-col gap-1.5 text-sm text-gray-700 dark:text-gray-300">
                  {notes.decisions.map((d, i) => (
                    <li key={i} className="rounded-lg bg-emerald-50 px-3 py-2 dark:bg-emerald-500/10">
                      {d}
                    </li>
                  ))}
                </ul>
              </section>
            )}
            <section>
              <h4 className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-gray-900 dark:text-white">
                <ListChecks className="size-4 text-brand-600" />
                {t('notes.actions')}
              </h4>
              {notes.actionItems.length === 0 ? (
                <p className="text-sm text-gray-500">{t('notes.noActions')}</p>
              ) : (
                <ul className="divide-y divide-gray-100 rounded-xl border border-gray-100 dark:divide-gray-800 dark:border-gray-800">
                  {notes.actionItems.map((a, i) => (
                    <li key={i} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2.5 text-sm">
                      <span className="min-w-0 flex-1 text-gray-900 dark:text-gray-100">{a.task}</span>
                      <span className="text-xs text-gray-500">{a.owner ?? t('notes.noOwner')}</span>
                      {a.due && (
                        <span className="inline-flex items-center gap-1 text-xs font-medium text-brand-700 dark:text-brand-300">
                          <CalendarCheck className="size-3.5" />
                          {fmt.date(`${a.due}T00:00`)}
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </>
        )}

        <div className="flex flex-col gap-2">
          <button
            type="button"
            onClick={() => setShowTranscript(!showTranscript)}
            className="inline-flex w-fit items-center gap-1 text-xs font-medium text-gray-500 hover:text-gray-900 dark:hover:text-white"
          >
            <ChevronDown className={showTranscript ? 'size-4 rotate-180' : 'size-4'} />
            {t('notes.transcript', { count: notes.lineCount })} · {t('common.minutes', { count: notes.minutes })}
          </button>
          {showTranscript &&
            (loadingLines && !lines ? (
              <Spinner className="size-4 text-brand-600" />
            ) : (
              <ol className="flex max-h-80 flex-col gap-1 overflow-y-auto rounded-xl bg-gray-50 p-3 text-sm dark:bg-gray-900">
                {(lines ?? []).map((l) => (
                  <li key={l.id} className="flex gap-2">
                    <span className="shrink-0 text-xs text-gray-400 tabular-nums">{fmt.time(l.at)}</span>
                    <span className="shrink-0 font-medium text-gray-900 dark:text-white">{l.speaker}</span>
                    <span className="text-gray-700 dark:text-gray-300">{l.text}</span>
                  </li>
                ))}
              </ol>
            ))}
        </div>
        <p className="text-xs text-gray-400">{t('notes.disclaimer')}</p>
      </div>
    </Card>
  )
}
