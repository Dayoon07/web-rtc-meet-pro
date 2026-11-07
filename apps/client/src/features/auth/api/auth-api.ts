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
  /** 가입하지 않은 이메일이어도 성공(204)으로 응답한다 */
  requestPasswordReset: (email: string) =>
    http<void>('/auth/password-reset/request', { method: 'POST', json: { email }, auth: false }),
  confirmPasswordReset: (token: string, password: string) =>
    http<void>('/auth/password-reset/confirm', { method: 'POST', json: { token, password }, auth: false }),
  /** 가입 인증 메일의 링크 (로그인 없이) */
  confirmEmail: (token: string) => http<void>('/auth/email-verification/confirm', { method: 'POST', json: { token }, auth: false }),
  providers: () => http<{ google: boolean }>('/auth/providers', { auth: false }),
  exchangeOAuthCode: (code: string) =>
    http<AuthTokens>('/auth/oauth/exchange', { method: 'POST', json: { code }, auth: false }),
}

export const useAuthProviders = () =>
  useQuery({ queryKey: ['auth', 'providers'], queryFn: authApi.providers, staleTime: Infinity })
