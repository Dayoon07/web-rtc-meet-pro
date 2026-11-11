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
  /** 방해 금지 시간 (HH:mm) · 시간대. 안 쓰면 null */
  dndStart?: string | null
  dndEnd?: string | null
  dndTimeZone?: string | null
  /** false 면 온보딩(추가 정보 입력)으로 보낸다 */
  profileCompleted: boolean
  hasPassword: boolean
  /** false 면 "이메일을 확인해 주세요" 화면으로 보낸다 (이메일 가입 후 인증 전) */
  emailVerified: boolean
  linkedProviders: string[]
  plan: Plan
}

/** 서버로 보내는 프로필 */
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

/** 방해 금지 시간. start·end 를 비우면 끄기 */
export type QuietHoursRequest = { start: string | null; end: string | null; timeZone: string | null }
