import { config } from './config.ts'

/** Spring /internal/media/events 로 보내는 이벤트. DB 기록은 서버가 한다 */
export type MediaEvent =
  | { type: 'JOINED'; meetingId: number; userId: number; displayName: string; ipAddress?: string; userAgent?: string }
  | { type: 'LEFT'; participantId: number; reason: 'LEFT' | 'DISCONNECTED' }
  | {
      type: 'EVENT'
      meetingId: number
      userId: number
      eventType:
        | 'SCREEN_SHARE_STARTED'
        | 'SCREEN_SHARE_STOPPED'
        | 'HAND_RAISED'
        | 'HAND_LOWERED'
        | 'MIC_ON'
        | 'MIC_OFF'
        | 'CAMERA_ON'
        | 'CAMERA_OFF'
    }
  | { type: 'ROOM_CLOSED'; meetingId: number }

/** 기록 실패가 회의 자체를 막지 않도록 에러는 로그만 남긴다 */
export async function report(event: MediaEvent): Promise<Record<string, unknown>> {
  try {
    const res = await fetch(`${config.serverUrl}/internal/media/events`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Internal-Secret': config.internalSecret },
      body: JSON.stringify(event),
    })
    if (!res.ok) {
      console.warn(`[report] ${event.type} → ${res.status}`)
      return {}
    }
    return (await res.json()) as Record<string, unknown>
  } catch (e) {
    console.warn(`[report] ${event.type} 실패:`, (e as Error).message)
    return {}
  }
}
