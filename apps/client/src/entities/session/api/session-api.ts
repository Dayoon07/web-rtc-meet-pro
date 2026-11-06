import { http } from '@/shared/api'
import type { AuthTokens } from '../model/session-store'

export const sessionApi = {
  refresh: (refreshToken: string) =>
    http<AuthTokens>('/auth/refresh', { method: 'POST', json: { refreshToken }, auth: false }),
  logout: (refreshToken: string) =>
    http<void>('/auth/logout', { method: 'POST', json: { refreshToken }, auth: false }),
}
