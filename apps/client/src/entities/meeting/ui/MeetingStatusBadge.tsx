import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import type { MeetingStatus } from '../model/types'

export function MeetingStatusBadge({ status }: { status: MeetingStatus }) {
  const { t } = useTranslation()
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-semibold',
        status === 'LIVE' && 'bg-red-50 text-red-600 dark:bg-red-500/15 dark:text-red-300',
        status === 'SCHEDULED' && 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-200',
        (status === 'ENDED' || status === 'CANCELED') && 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
      )}
    >
      {status === 'LIVE' && <span className="size-1.5 animate-pulse rounded-full bg-red-500" />}
      {t(`meetings.status.${status}`)}
    </span>
  )
}
