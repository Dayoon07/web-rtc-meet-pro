const DIAL_CODES: Record<string, string> = {
  KR: '82', US: '1', JP: '81', CN: '86', TW: '886', HK: '852', SG: '65', VN: '84', TH: '66',
  PH: '63', ID: '62', MY: '60', IN: '91', AU: '61', NZ: '64', CA: '1', GB: '44', DE: '49',
  FR: '33', ES: '34', IT: '39', NL: '31', SE: '46', BR: '55', MX: '52',
}

export type Country = { code: string; dialCode: string }

export const COUNTRIES: Country[] = Object.entries(DIAL_CODES).map(([code, dialCode]) => ({ code, dialCode }))

export const DEFAULT_COUNTRY = 'KR'

export const findCountry = (code: string | null | undefined) => COUNTRIES.find((c) => c.code === code)

const TRUNK_ZERO = new Set(['KR', 'JP', 'CN', 'TW', 'VN', 'TH', 'ID', 'MY', 'PH', 'IN', 'AU', 'NZ', 'GB', 'DE', 'FR', 'NL', 'SE'])

export function splitPhone(e164: string | null | undefined, preferred?: string | null) {
  if (!e164?.startsWith('+')) return { local: e164 ?? '' }
  const digits = e164.slice(1)
  const candidates = COUNTRIES.filter((c) => digits.startsWith(c.dialCode))
  const match = candidates.find((c) => c.code === preferred) ?? candidates.sort((a, b) => b.dialCode.length - a.dialCode.length)[0]
  if (!match) return { local: e164 }
  const national = digits.slice(match.dialCode.length)
  return { countryCode: match.code, local: TRUNK_ZERO.has(match.code) ? `0${national}` : national }
}

export function toE164(dialCode: string, local: string) {
  const digits = local.replace(/\D/g, '').replace(/^0+/, '')
  return digits ? `+${dialCode}${digits}` : ''
}

const thisYear = new Date().getFullYear()
export const MIN_AGE = 14
export const BIRTH_YEARS = Array.from({ length: thisYear - MIN_AGE - 1 - 1900 + 1 }, (_, i) => thisYear - MIN_AGE - 1 - i)

export function guessCountry(lang: string): string {
  return ({ ko: 'KR', ja: 'JP', zh: 'CN', en: 'US' } as Record<string, string>)[lang.slice(0, 2)] ?? DEFAULT_COUNTRY
}
