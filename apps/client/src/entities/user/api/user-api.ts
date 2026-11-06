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
}

export const useMeQuery = (enabled = true) => useQuery({ queryKey: userKeys.me, queryFn: userApi.me, enabled })
