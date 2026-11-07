import { useQuery } from '@tanstack/react-query'
import type { AuthTokens } from '@/entities/session'
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
  /** 비밀번호 바꾸기·만들기(구글 전용 계정은 current 없이). 다른 기기는 로그아웃되고 이 기기용 새 토큰을 준다 */
  changePassword: (currentPassword: string | null, newPassword: string) =>
    http<AuthTokens>('/users/me/password', { method: 'POST', json: { currentPassword, newPassword } }),
  /** 가입 인증 메일 다시 보내기 (60초에 한 번) */
  resendVerification: () => http<void>('/users/me/email-verification', { method: 'POST' }),
  /** 회원 탈퇴. 비밀번호가 있는 계정은 비밀번호를 함께 보낸다 */
  withdraw: (password?: string) => http<void>('/users/me/withdraw', { method: 'POST', json: { password: password ?? null } }),
}

export const useMeQuery = (enabled = true) => useQuery({ queryKey: userKeys.me, queryFn: userApi.me, enabled })
