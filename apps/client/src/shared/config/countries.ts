/** 회원가입 국가 / 전화 국가번호. 국가명은 화면에서 현재 언어로 Intl.DisplayNames 로 표시 */
const DIAL_CODES: Record<string, string> = {
  KR: '82', US: '1', JP: '81', CN: '86', TW: '886', HK: '852', SG: '65', VN: '84', TH: '66',
  PH: '63', ID: '62', MY: '60', IN: '91', AU: '61', NZ: '64', CA: '1', GB: '44', DE: '49',
  FR: '33', ES: '34', IT: '39', NL: '31', SE: '46', BR: '55', MX: '52',
}

/** 국기 이모지는 Windows 에서 글자(KR)로 보여서 쓰지 않는다 */
export type Country = { code: string; dialCode: string }

export const COUNTRIES: Country[] = Object.entries(DIAL_CODES).map(([code, dialCode]) => ({ code, dialCode }))

export const DEFAULT_COUNTRY = 'KR'

export const findCountry = (code: string | null | undefined) => COUNTRIES.find((c) => c.code === code)

/** 국내에서 앞에 0 을 붙여 거는 나라 (한국 010-…, 일본 090-… 등) */
const TRUNK_ZERO = new Set(['KR', 'JP', 'CN', 'TW', 'VN', 'TH', 'ID', 'MY', 'PH', 'IN', 'AU', 'NZ', 'GB', 'DE', 'FR', 'NL', 'SE'])

/** +821012345678 → { countryCode: 'KR', local: '01012345678' } (국가번호가 겹치면 선택한 국가 우선) */
export function splitPhone(e164: string | null | undefined, preferred?: string | null) {
  if (!e164?.startsWith('+')) return { local: e164 ?? '' }
  const digits = e164.slice(1)
  const candidates = COUNTRIES.filter((c) => digits.startsWith(c.dialCode))
  const match = candidates.find((c) => c.code === preferred) ?? candidates.sort((a, b) => b.dialCode.length - a.dialCode.length)[0]
  if (!match) return { local: e164 }
  const national = digits.slice(match.dialCode.length)
  return { countryCode: match.code, local: TRUNK_ZERO.has(match.code) ? `0${national}` : national }
}

/** 국가번호 + 국내 번호 → E.164. 국내 번호 앞의 0 은 뗀다 (010 → 10) */
export function toE164(dialCode: string, local: string) {
  const digits = local.replace(/\D/g, '').replace(/^0+/, '')
  return digits ? `+${dialCode}${digits}` : ''
}

const thisYear = new Date().getFullYear()
export const MIN_AGE = 14
/** 출생 연도만 받으므로 생일 전 만 13세가 고를 수 없게 한 해 더 (서버 User 와 같은 규칙) */
export const BIRTH_YEARS = Array.from({ length: thisYear - MIN_AGE - 1 - 1900 + 1 }, (_, i) => thisYear - MIN_AGE - 1 - i)

/** 브라우저 언어 → 가입 기본 국가 */
export function guessCountry(lang: string): string {
  return ({ ko: 'KR', ja: 'JP', zh: 'CN', en: 'US' } as Record<string, string>)[lang.slice(0, 2)] ?? DEFAULT_COUNTRY
}
