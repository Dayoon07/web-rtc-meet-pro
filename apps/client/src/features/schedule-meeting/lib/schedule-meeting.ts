export const DURATIONS = [15, 30, 45, 60, 90, 120]

const pad = (n: number) => String(n).padStart(2, '0')

/** 다음 정각 또는 30분 (예: 10:12 → 10:30) */
export function nextSlot() {
  const d = new Date()
  d.setMinutes(d.getMinutes() < 30 ? 30 : 60, 0, 0)
  return { date: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`, time: `${pad(d.getHours())}:${pad(d.getMinutes())}` }
}

/** 회의 시각(ISO) → 날짜·시간 칸 값 (이 브라우저 시간대) */

/** 회의 시각(ISO) → 날짜·시간 칸 값 (이 브라우저 시간대) */
export function slotOf(iso: string) {
  const d = new Date(iso)
  return { date: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`, time: `${pad(d.getHours())}:${pad(d.getMinutes())}` }
}
