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
  /** 비밀번호 바꾸기·만들기(구글 전용 계정은 current 없이). 다른 기기는 로그아웃되고 이 기기용 새 토큰을 준다 */
  changePassword: (currentPassword: string | null, newPassword: string) =>
    request<AuthTokens>(API.me.password, { method: 'POST', json: { currentPassword, newPassword } satisfies ChangePasswordRequest }),
  /** 가입 인증 메일 다시 보내기 (60초에 한 번) */
  resendVerification: () => request<void>(API.me.emailVerification, { method: 'POST' }),
  /** 방해 금지 시간 (이 시간에는 브라우저 푸시를 보내지 않는다) */
  updateQuietHours: (body: QuietHoursRequest) => request<Me>(API.me.quietHours, { method: 'PUT', json: body }),
  /** 회원 탈퇴. 비밀번호가 있는 계정은 비밀번호를 함께 보낸다 */
  withdraw: (password?: string) => request<void>(API.me.withdraw, { method: 'POST', json: { password: password ?? null } satisfies WithdrawRequest }),
}

export const useMeQuery = (enabled = true) => useQuery({ queryKey: userKeys.me, queryFn: userApi.me, enabled })
