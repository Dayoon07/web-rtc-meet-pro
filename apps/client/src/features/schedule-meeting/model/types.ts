import type { Meeting } from '@/entities/meeting'

export type ScheduleTarget = { mode: 'now' | 'schedule' | 'edit'; teamId?: number; meeting?: Meeting }
