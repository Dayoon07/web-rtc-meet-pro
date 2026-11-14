import { BellRing, MoonStar } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { Me } from '@/entities/user'
import { cn } from '@/shared/lib/cn'
import { Button, Checkbox } from '@/shared/ui'
import { usePushToggle } from '../model/use-push-toggle'
import { useQuietHours } from '../model/use-quiet-hours'

const timeInput =
  'h-10 rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-900 outline-none focus:border-brand-500 focus:ring-3 focus:ring-brand-500/15 disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100'

function PushRow() {
  const { t } = useTranslation()
  const push = usePushToggle()
  const on = push.state === 'on'
  const canToggle = push.state === 'on' || push.state === 'off'
  return (
    <div className="flex items-start gap-3">
      <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300">
        <BellRing className="size-4.5" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-gray-900 dark:text-white">{t('notifySettings.push.title')}</p>
        <p className="text-xs text-gray-500 dark:text-gray-400">{t(`notifySettings.push.state.${push.state}`)}</p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={on}
        aria-label={t('notifySettings.push.title')}
        disabled={!canToggle || push.busy || !push.ready}
        onClick={() => void push.toggle()}
        className={cn('relative h-6 w-11 shrink-0 rounded-full transition-colors disabled:opacity-40', on ? 'bg-brand-600' : 'bg-gray-300 dark:bg-gray-700')}
      >
        <span className={cn('absolute top-0.5 left-0.5 size-5 rounded-full bg-white shadow transition-transform', on && 'translate-x-5')} />
      </button>
    </div>
  )
}

function QuietHoursRow({ me }: { me: Me }) {
  const { t } = useTranslation()
  const q = useQuietHours(me)
  return (
    <div className="flex items-start gap-3">
      <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600 dark:bg-indigo-500/15 dark:text-indigo-300">
        <MoonStar className="size-4.5" />
      </span>
      <div className="min-w-0 flex-1">
        <Checkbox
          label={<span className="font-medium">{t('notifySettings.quiet.title')}</span>}
          description={t('notifySettings.quiet.desc')}
          checked={q.enabled}
          onChange={(e) => q.setEnabled(e.target.checked)}
        />
        <div className="mt-3 flex flex-wrap items-center gap-2 pl-7">
          <input type="time" aria-label={t('notifySettings.quiet.start')} value={q.start} disabled={!q.enabled} onChange={(e) => q.setStart(e.target.value)} className={timeInput} />
          <span className="text-sm text-gray-500">~</span>
          <input type="time" aria-label={t('notifySettings.quiet.end')} value={q.end} disabled={!q.enabled} onChange={(e) => q.setEnd(e.target.value)} className={timeInput} />
          <Button size="sm" loading={q.saving} disabled={!q.changed} onClick={q.save}>
            {t('common.save')}
          </Button>
        </div>
      </div>
    </div>
  )
}

export function NotificationSettings({ me }: { me: Me }) {
  return (
    <div className="flex flex-col gap-5">
      <PushRow />
      <QuietHoursRow me={me} />
    </div>
  )
}
