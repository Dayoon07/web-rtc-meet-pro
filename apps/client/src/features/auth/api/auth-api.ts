import { useQuery } from '@tanstack/react-query'
import type { AuthTokens } from '@/entities/session'
import type { Agreements, ProfileRequest } from '@/entities/user'
import { API, request } from '@/shared/api'

export type LoginRequest = { email: string; password: string }
export type SignupRequest = LoginRequest & ProfileRequest & Agreements
export type EmailRequest = { email: string }
export type TokenRequest = { token: string }
export type ResetPasswordRequest = { token: string; password: string }
export type OAuthExchangeRequest = { code: string }
export type EmailAvailabilityResponse = { available: boolean }
export type ProvidersResponse = { google: boolean }

export const authApi = {
  login: (body: LoginRequest) => request<AuthTokens>(API.auth.login, { method: 'POST', json: body, auth: false }),
  signup: (body: SignupRequest) => request<AuthTokens>(API.auth.signup, { method: 'POST', json: body, auth: false }),
  emailAvailable: (email: string) =>
    request<EmailAvailabilityResponse>(API.auth.emailAvailability(email), { auth: false }),
  /** 가입하지 않은 이메일이어도 성공(204)으로 응답한다 */
  requestPasswordReset: (email: string) =>
    request<void>(API.auth.passwordResetRequest, { method: 'POST', json: { email } satisfies EmailRequest, auth: false }),
  confirmPasswordReset: (token: string, password: string) =>
    request<void>(API.auth.passwordResetConfirm, { method: 'POST', json: { token, password } satisfies ResetPasswordRequest, auth: false }),
  /** 가입 인증 메일의 링크 (로그인 없이) */
  confirmEmail: (token: string) => request<void>(API.auth.emailVerificationConfirm, { method: 'POST', json: { token } satisfies TokenRequest, auth: false }),
  providers: () => request<ProvidersResponse>(API.auth.providers, { auth: false }),
  exchangeOAuthCode: (code: string) =>
    request<AuthTokens>(API.auth.oauthExchange, { method: 'POST', json: { code } satisfies OAuthExchangeRequest, auth: false }),
}

export const useAuthProviders = () =>
  useQuery({ queryKey: ['auth', 'providers'], queryFn: authApi.providers, staleTime: Infinity })
