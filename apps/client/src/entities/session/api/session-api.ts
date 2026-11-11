import { API, request } from '@/shared/api'
import type { AuthTokens } from '../model/session-store'

export type RefreshTokenRequest = { refreshToken: string }

export const sessionApi = {
  refresh: (refreshToken: string) =>
    request<AuthTokens>(API.auth.refresh, { method: 'POST', json: { refreshToken } satisfies RefreshTokenRequest, auth: false }),
  logout: (refreshToken: string) =>
    request<void>(API.auth.logout, { method: 'POST', json: { refreshToken } satisfies RefreshTokenRequest, auth: false }),
}
