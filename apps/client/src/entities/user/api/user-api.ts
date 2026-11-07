import { useQuery } from '@tanstack/react-query'
import { http } from '@/shared/api'
import type { Agreements, Me, ProfilePayload } from '../model/types'

export const userKeys = { me: ['users', 'me'] as const }

export const userApi = {
  me: () => http<Me>('/users/me'),
  updateProfile: (body: ProfilePayload & Partial<Agreements>) => http<Me>('/users/me/profile', { method: 'PUT', json: body }),
  uploadProfileImage: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return http<Me>('/users/me/profile-image', { method: 'POST', form })
  },
  removeProfileImage: () => http<Me>('/users/me/profile-image', { method: 'DELETE' }),
  /** 회원 탈퇴. 비밀번호가 있는 계정은 비밀번호를 함께 보낸다 */
  withdraw: (password?: string) => http<void>('/users/me/withdraw', { method: 'POST', json: { password: password ?? null } }),
}

export const useMeQuery = (enabled = true) => useQuery({ queryKey: userKeys.me, queryFn: userApi.me, enabled })
