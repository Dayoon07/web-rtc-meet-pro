import type { Meeting } from '@/entities/meeting'

/** 회의 창을 무엇으로 열지: 지금 시작 / 예약 / 고치기. teamId = 미리 고를 워크스페이스, meeting = 고칠 회의 */
export type ScheduleTarget = { mode: 'now' | 'schedule' | 'edit'; teamId?: number; meeting?: Meeting }
