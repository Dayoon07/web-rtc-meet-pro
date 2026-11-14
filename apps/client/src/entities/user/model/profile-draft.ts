import type { TFunction } from 'i18next'
import { findCountry, guessCountry, splitPhone, toE164 } from '@/shared/config/countries'
import { currentLanguage } from '@/shared/i18n'
import type { Gender, Me, ProfileRequest } from './types'

export type ProfileDraft = {
  nickname: string
  realName: string
  countryCode: string
  phoneLocal: string
  birthYear: string
  gender: Gender | null
}

export type ProfileErrors = Partial<Record<keyof ProfileDraft, string>>

export const emptyProfileDraft = (): ProfileDraft => ({
  nickname: '',
  realName: '',
  countryCode: guessCountry(currentLanguage()),
  phoneLocal: '',
  birthYear: '',
  gender: null,
})

export function draftFromMe(me: Me): ProfileDraft {
  const phone = splitPhone(me.phoneNumber, me.countryCode)
  return {
    nickname: me.nickname,
    realName: me.realName ?? (me.profileCompleted ? '' : me.nickname),
    countryCode: me.countryCode ?? phone.countryCode ?? guessCountry(currentLanguage()),
    phoneLocal: phone.local,
    birthYear: me.birthYear ? String(me.birthYear) : '',
    gender: me.gender,
  }
}

export function validateProfile(d: ProfileDraft, t: TFunction): ProfileErrors {
  const errors: ProfileErrors = {}
  if (!d.realName.trim()) errors.realName = t('profile.validation.realName')
  if (!d.nickname.trim()) errors.nickname = t('profile.validation.nickname')
  if (!d.countryCode) errors.countryCode = t('profile.validation.country')
  const digits = d.phoneLocal.replace(/\D/g, '')
  if (!digits) errors.phoneLocal = t('profile.validation.phone')
  else if (digits.length < 6 || digits.length > 13) errors.phoneLocal = t('profile.validation.phoneInvalid')
  if (!d.birthYear) errors.birthYear = t('profile.validation.birthYear')
  return errors
}

export function toProfileRequest(d: ProfileDraft): ProfileRequest {
  const dial = findCountry(d.countryCode)?.dialCode ?? '82'
  return {
    nickname: d.nickname.trim(),
    realName: d.realName.trim(),
    countryCode: d.countryCode,
    phoneNumber: toE164(dial, d.phoneLocal),
    birthYear: Number(d.birthYear),
    gender: d.gender,
  }
}
