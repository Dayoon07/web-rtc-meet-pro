import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, Film, Play, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { type Meeting, meetingApi, meetingKeys, type Recording } from '@/entities/meeting'
import { toastError } from '@/shared/api'
import { useFormat } from '@/shared/lib/format'
import { Badge, Button, Card } from '@/shared/ui'
import { duration, mb } from '../lib/meeting-recorder'

export function RecordingList({ meeting }: { meeting: Meeting }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const queryClient = useQueryClient()
  const [playing, setPlaying] = useState<number | null>(null)
  const { data: recordings = [] } = useQuery({
    queryKey: meetingKeys.recordings(meeting.id),
    queryFn: () => meetingApi.recordings(meeting.id),
    refetchInterval: (q) => ((q.state.data ?? []).some((r) => r.status === 'RECORDING') ? 15_000 : false),
  })
  const remove = useMutation({
    mutationFn: (r: Recording) => meetingApi.deleteRecording(meeting.id, r.id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: meetingKeys.recordings(meeting.id) }),
    onError: (e) => toastError(e),
  })

  if (!recordings.length) return null
  return (
    <Card className="p-0">
      <h3 className="flex items-center gap-2 border-b border-gray-100 px-5 py-3.5 font-semibold text-gray-900 dark:border-gray-800 dark:text-white">
        <Film className="size-4 text-brand-600" />
        {t('meetings.recordings.title')}
      </h3>
      <ul className="divide-y divide-gray-100 dark:divide-gray-800">
        {recordings.map((r) => (
          <li key={r.id} className="flex flex-col gap-3 px-5 py-3.5">
            <div className="flex flex-wrap items-center gap-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-900 dark:text-white">
                  {fmt.dateTime(r.startedAt)} {r.durationSeconds != null && <span className="text-gray-500">· {duration(r.durationSeconds)}</span>}
                </p>
                <p className="text-xs text-gray-500">
                  {t('meetings.recordings.by', { name: r.startedBy?.nickname ?? '-' })} · {mb(r.sizeBytes)} MB
                </p>
              </div>
              {r.status === 'RECORDING' && <Badge tone="brand">{t('meetings.recordings.inProgress')}</Badge>}
              {r.status === 'FAILED' && <Badge tone="gray">{t('meetings.recordings.failed')}</Badge>}
              {r.url && (
                <>
                  <Button size="sm" variant="outline" icon={<Play className="size-4" />} onClick={() => setPlaying(playing === r.id ? null : r.id)}>
                    {t('meetings.recordings.play')}
                  </Button>
                  <a
                    href={`${r.url}&download=true`}
                    className="inline-flex h-8 items-center gap-1.5 rounded-lg px-3 text-sm font-semibold text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
                  >
                    <Download className="size-4" />
                    <span className="hidden sm:inline">{t('meetings.recordings.download')}</span>
                  </a>
                </>
              )}
              {r.canDelete && (
                <Button
                  size="sm"
                  variant="ghost"
                  aria-label={t('meetings.recordings.delete')}
                  loading={remove.isPending && remove.variables?.id === r.id}
                  onClick={() => window.confirm(t('meetings.recordings.deleteConfirm')) && remove.mutate(r)}
                  icon={<Trash2 className="size-4" />}
                />
              )}
            </div>
            {playing === r.id && r.url && (
              // eslint-disable-next-line jsx-a11y/media-has-caption -- 회의 녹화에는 자막 파일이 없다
              <video src={r.url} controls autoPlay className="aspect-video w-full rounded-xl bg-black" />
            )}
          </li>
        ))}
      </ul>
    </Card>
  )
}
