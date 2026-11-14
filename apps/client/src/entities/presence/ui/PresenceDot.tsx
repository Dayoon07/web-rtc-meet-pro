import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import type { PresenceStatus } from '../model/types'

export function PresenceDot({ status }: { status: PresenceStatus }) {
  const { t } = useTranslation()
  return (
    <span
      role="img"
      aria-label={t(`presence.${status}`)}
      title={t(`presence.${status}`)}
      className={cn(
        'absolute -right-0.5 -bottom-0.5 size-3 rounded-full ring-2 ring-white dark:ring-gray-950',
        status === 'ONLINE' && 'bg-green-500',
        status === 'AWAY' && 'bg-amber-400',
        status === 'OFFLINE' && 'bg-gray-300 dark:bg-gray-600',
      )}
    />
  )
}
