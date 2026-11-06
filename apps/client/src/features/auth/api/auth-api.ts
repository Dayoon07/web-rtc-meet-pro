import { useQuery } from '@tanstack/react-query'
import type { AuthTokens } from '@/entities/session'
import type { Agreements, ProfilePayload } from '@/entities/user'
import { http } from '@/shared/api'

export type LoginBody = { email: string; password: string }
export type SignupBody = LoginBody & ProfilePayload & Agreements

export const authApi = {
  login: (body: LoginBody) => http<AuthTokens>('/auth/login', { method: 'POST', json: body, auth: false }),
  signup: (body: SignupBody) => http<AuthTokens>('/auth/signup', { method: 'POST', json: body, auth: false }),
  emailAvailable: (email: string) =>
    http<{ available: boolean }>(`/auth/email-availability?email=${encodeURIComponent(email)}`, { auth: false }),
  providers: () => http<{ google: boolean }>('/auth/providers', { auth: false }),
  exchangeOAuthCode: (code: string) =>
    http<AuthTokens>('/auth/oauth/exchange', { method: 'POST', json: { code }, auth: false }),
}

export const useAuthProviders = () =>
  useQuery({ queryKey: ['auth', 'providers'], queryFn: authApi.providers, staleTime: Infinity })
