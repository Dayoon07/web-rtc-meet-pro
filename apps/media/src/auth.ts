import { jwtVerify } from 'jose'
import { config } from './config.ts'

/** Spring MediaTokenIssuer 가 넣는 클레임 */
export type MediaClaims = {
  userId: number
  meetingId: number
  roomCode: string
  name: string
  avatar: string | null
  isHost: boolean
}

const key = new TextEncoder().encode(config.jwtSecret)

export async function verifyMediaToken(token: string): Promise<MediaClaims> {
  const { payload } = await jwtVerify(token, key, {
    algorithms: ['HS256'],
    audience: config.jwtAudience,
    issuer: config.jwtIssuer,
  })
  const userId = Number(payload.sub)
  const meetingId = Number(payload.mid)
  if (!Number.isFinite(userId) || !Number.isFinite(meetingId)) throw new Error('invalid claims')
  return {
    userId,
    meetingId,
    roomCode: String(payload.room ?? ''),
    name: String(payload.name ?? ''),
    avatar: typeof payload.avatar === 'string' ? payload.avatar : null,
    isHost: payload.host === true,
  }
}
