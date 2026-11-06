import { useTranslation } from 'react-i18next'
import { Checkbox, FieldError } from '@/shared/ui'
import type { Agreements } from '../model/types'

const ITEMS = [
  { key: 'termsAgreed', label: 'terms.terms', required: true },
  { key: 'privacyAgreed', label: 'terms.privacy', required: true },
  { key: 'marketingAgreed', label: 'terms.marketing', required: false },
] as const

export const emptyAgreements = (): Agreements => ({ termsAgreed: false, privacyAgreed: false, marketingAgreed: false })
export const requiredAgreed = (a: Agreements) => a.termsAgreed && a.privacyAgreed

/** 국내 서비스에서 익숙한 "전체 동의 + 필수/선택" 약관 체크 */
export function TermsAgreement({ value, onChange, error }: { value: Agreements; onChange: (v: Agreements) => void; error?: string | null }) {
  const { t } = useTranslation()
  const all = ITEMS.every((i) => value[i.key])
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-gray-200 p-4 dark:border-gray-800">
      <Checkbox
        label={<span className="font-semibold">{t('terms.all')}</span>}
        checked={all}
        onChange={(e) => onChange({ termsAgreed: e.target.checked, privacyAgreed: e.target.checked, marketingAgreed: e.target.checked })}
      />
      <div className="h-px bg-gray-200 dark:bg-gray-800" />
      {ITEMS.map((item) => (
        <Checkbox
          key={item.key}
          label={
            <>
              <span className={item.required ? 'text-brand-700 dark:text-brand-300' : 'text-gray-500'}>
                ({t(item.required ? 'terms.required' : 'terms.optional')})
              </span>{' '}
              {t(item.label)}
            </>
          }
          checked={value[item.key]}
          onChange={(e) => onChange({ ...value, [item.key]: e.target.checked })}
        />
      ))}
      <FieldError>{error}</FieldError>
    </div>
  )
}
