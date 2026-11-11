import type { TFunction } from 'i18next'
import { translateDynamic } from '@/shared/i18n'
import type { AppNotification } from '../model/types'

/** 서버는 type + params 만 보낸다 → 사용자 언어로 문장 생성 (시간은 사용자 시간대로) */
export function notificationText(n: AppNotification, t: TFunction, formatTime: (iso: string) => string) {
  const p = n.params
  const time = typeof p.scheduledAt === 'string' ? formatTime(p.scheduledAt) : ''
  const date = typeof p.date === 'string' ? formatTime(p.date) : ''
  // 플랜 이름은 코드로 번역 (없으면 서버가 보낸 이름)
  const plan = typeof p.planCode === 'string' ? translateDynamic(`plan.names.${p.planCode}`, undefined, String(p.planName ?? p.planCode)) : ''
  // 금액은 1,000 단위 쉼표
  const amount = typeof p.amount === 'number' ? p.amount.toLocaleString() : p.amount
  return t(`notifications.types.${n.type}`, { ...p, time, date, plan, amount })
}
