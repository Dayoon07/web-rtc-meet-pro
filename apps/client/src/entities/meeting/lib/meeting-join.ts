import type { Meeting } from '../model/types'

const EARLY_JOIN_MS = 10 * 60 * 1000

/** 서버 규칙과 같게: 진행 중이거나, 주최자이거나, 시작 10분 전부터 */
export function canJoinNow(m: Meeting, myId: number | undefined, now = Date.now()) {
  if (m.status === 'LIVE') return true
  // 모두 나가서 끝났어도 예정 시간 안이면 다시 열 수 있다 (서버 Meeting.canReopen 과 같은 규칙)
  // 채널 음성방은 끝나면 다시 열지 않는다 (채널에서 새로 들어간다)
  if (m.status === 'ENDED') return !m.chatRoomId && now < new Date(m.scheduledAt).getTime() + m.durationMinutes * 60_000
  if (m.status !== 'SCHEDULED') return false
  return m.host?.id === myId || now >= new Date(m.scheduledAt).getTime() - EARLY_JOIN_MS
}

export const joinOpensAt = (m: Meeting) => new Date(new Date(m.scheduledAt).getTime() - EARLY_JOIN_MS)
