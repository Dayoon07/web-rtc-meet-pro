import { useQuery } from '@tanstack/react-query'
import type { AuthTokens } from '@/entities/session'
import { API, request } from '@/shared/api'
import type { Agreements, ChangePasswordRequest, Me, ProfileRequest, QuietHoursRequest, WithdrawRequest } from '../model/types'

export const userKeys = { me: ['users', 'me'] as const }

export const userApi = {
  me: () => request<Me>(API.me.root),
  updateProfile: (body: ProfileRequest & Partial<Agreements>) => request<Me>(API.me.profile, { method: 'PUT', json: body }),
  uploadProfileImage: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<Me>(API.me.profileImage, { method: 'POST', form })
  },
  removeProfileImage: () => request<Me>(API.me.profileImage, { method: 'DELETE' }),
  changePassword: (currentPassword: string | null, newPassword: string) =>
    request<AuthTokens>(API.me.password, { method: 'POST', json: { currentPassword, newPassword } satisfies ChangePasswordRequest }),
  resendVerification: () => request<void>(API.me.emailVerification, { method: 'POST' }),
  updateQuietHours: (body: QuietHoursRequest) => request<Me>(API.me.quietHours, { method: 'PUT', json: body }),
  withdraw: (password?: string) => request<void>(API.me.withdraw, { method: 'POST', json: { password: password ?? null } satisfies WithdrawRequest }),
}

export const useMeQuery = (enabled = true) => useQuery({ queryKey: userKeys.me, queryFn: userApi.me, enabled })
