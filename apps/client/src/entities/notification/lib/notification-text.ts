import type { TFunction } from 'i18next'
import { translateDynamic } from '@/shared/i18n'
import type { AppNotification } from '../model/types'

export function notificationText(n: AppNotification, t: TFunction, formatTime: (iso: string) => string) {
  const p = n.params
  const time = typeof p.scheduledAt === 'string' ? formatTime(p.scheduledAt) : ''
  const date = typeof p.date === 'string' ? formatTime(p.date) : ''
  const plan = typeof p.planCode === 'string' ? translateDynamic(`plan.names.${p.planCode}`, undefined, String(p.planName ?? p.planCode)) : ''
  const amount = typeof p.amount === 'number' ? p.amount.toLocaleString() : p.amount
  return t(`notifications.types.${n.type}`, { ...p, time, date, plan, amount })
}
