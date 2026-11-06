import { useId, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { BIRTH_YEARS, COUNTRIES, findCountry } from '@/shared/config/countries'
import { useFormat } from '@/shared/lib/format'
import { Field, Input, Segmented, Select } from '@/shared/ui'
import type { ProfileDraft, ProfileErrors } from '../model/profile-draft'
import type { Gender } from '../model/types'

type Props = {
  value: ProfileDraft
  onChange: (next: ProfileDraft) => void
  errors?: ProfileErrors
  disabled?: boolean
}

/** 회원가입 2단계 / 구글 가입 온보딩 / 프로필 설정에서 함께 쓰는 개인정보 입력 */
export function ProfileFields({ value, onChange, errors = {}, disabled }: Props) {
  const { t, i18n } = useTranslation()
  const fmt = useFormat()
  const id = useId()
  const set = <K extends keyof ProfileDraft>(key: K, v: ProfileDraft[K]) => onChange({ ...value, [key]: v })
  const country = findCountry(value.countryCode)

  // 국가명은 현재 언어로, 가나다/ABC 순 (선택한 국가는 맨 위)
  const countries = useMemo(
    () =>
      COUNTRIES.map((c) => ({ ...c, name: fmt.region(c.code) })).sort((a, b) =>
        a.code === value.countryCode ? -1 : b.code === value.countryCode ? 1 : a.name.localeCompare(b.name, i18n.language),
      ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [i18n.language],
  )

  const genders: { value: Gender; label: string }[] = [
    { value: 'MALE', label: t('profile.male') },
    { value: 'FEMALE', label: t('profile.female') },
    { value: 'OTHER', label: t('profile.other') },
  ]

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label={t('profile.realName')} htmlFor={`${id}-real`} required error={errors.realName} hint={t('profile.realNameHint')}>
        <Input
          id={`${id}-real`}
          autoComplete="name"
          maxLength={50}
          placeholder={t('profile.realNamePlaceholder')}
          value={value.realName}
          invalid={!!errors.realName}
          disabled={disabled}
          onChange={(e) => set('realName', e.target.value)}
        />
      </Field>

      <Field label={t('profile.nickname')} htmlFor={`${id}-nick`} required error={errors.nickname} hint={t('profile.nicknameHint')}>
        <Input
          id={`${id}-nick`}
          autoComplete="nickname"
          maxLength={50}
          placeholder={t('profile.nicknamePlaceholder')}
          value={value.nickname}
          invalid={!!errors.nickname}
          disabled={disabled}
          onChange={(e) => set('nickname', e.target.value)}
        />
      </Field>

      <Field label={t('profile.country')} htmlFor={`${id}-country`} required error={errors.countryCode}>
        <Select
          id={`${id}-country`}
          autoComplete="country"
          value={value.countryCode}
          disabled={disabled}
          onChange={(e) => set('countryCode', e.target.value)}
        >
          {countries.map((c) => (
            <option key={c.code} value={c.code}>
              {c.name} (+{c.dialCode})
            </option>
          ))}
        </Select>
      </Field>

      <Field label={t('profile.birthYear')} htmlFor={`${id}-birth`} required error={errors.birthYear} hint={t('profile.birthYearHint')}>
        <Select
          id={`${id}-birth`}
          autoComplete="bday-year"
          value={value.birthYear}
          invalid={!!errors.birthYear}
          disabled={disabled}
          onChange={(e) => set('birthYear', e.target.value)}
        >
          <option value="" disabled>
            {t('profile.birthYearPlaceholder')}
          </option>
          {BIRTH_YEARS.map((y) => (
            <option key={y} value={y}>
              {t('profile.yearOption', { year: y })}
            </option>
          ))}
        </Select>
      </Field>

      <Field label={t('profile.phone')} htmlFor={`${id}-phone`} required error={errors.phoneLocal} className="sm:col-span-2">
        <div className="flex">
          <span className="inline-flex h-11 shrink-0 items-center gap-1.5 rounded-l-lg border border-r-0 border-gray-300 bg-gray-50 px-3 text-sm text-gray-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">
            +{country?.dialCode}
          </span>
          <Input
            id={`${id}-phone`}
            type="tel"
            inputMode="tel"
            autoComplete="tel-national"
            placeholder={t('profile.phonePlaceholder')}
            className="rounded-l-none"
            value={value.phoneLocal}
            invalid={!!errors.phoneLocal}
            disabled={disabled}
            onChange={(e) => set('phoneLocal', e.target.value)}
          />
        </div>
      </Field>

      <Field label={t('profile.gender')} hint={t('common.optional')} className="sm:col-span-2">
        <Segmented name={t('profile.gender')} value={value.gender} options={genders} onChange={(g) => set('gender', g)} />
      </Field>
    </div>
  )
}
