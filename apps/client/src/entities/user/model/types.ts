import type { Plan } from '@/entities/plan'

export type Gender = 'MALE' | 'FEMALE' | 'OTHER'

export type Me = {
  id: number
  email: string
  nickname: string
  realName: string | null
  phoneNumber: string | null
  countryCode: string | null
  birthYear: number | null
  gender: Gender | null
  profileImageUrl: string | null
  marketingAgreed: boolean
  dndStart?: string | null
  dndEnd?: string | null
  dndTimeZone?: string | null
  profileCompleted: boolean
  hasPassword: boolean
  emailVerified: boolean
  linkedProviders: string[]
  plan: Plan
}

export type ProfileRequest = {
  nickname: string
  realName: string
  phoneNumber: string
  countryCode: string
  birthYear: number
  gender: Gender | null
}

export type Agreements = { termsAgreed: boolean; privacyAgreed: boolean; marketingAgreed: boolean }

export type ChangePasswordRequest = { currentPassword: string | null; newPassword: string }
export type WithdrawRequest = { password: string | null }

export type QuietHoursRequest = { start: string | null; end: string | null; timeZone: string | null }
