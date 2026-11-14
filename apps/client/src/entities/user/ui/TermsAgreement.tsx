import { useTranslation } from 'react-i18next'
import { Checkbox, FieldError } from '@/shared/ui'
import type { Agreements } from '../model/types'
import { ROUTES } from '@/shared/config/routes'

const ITEMS = [
  { key: 'termsAgreed', label: 'terms.terms', required: true, doc: ROUTES.terms },
  { key: 'privacyAgreed', label: 'terms.privacy', required: true, doc: ROUTES.privacy },
  { key: 'marketingAgreed', label: 'terms.marketing', required: false, doc: null },
] as const

export const emptyAgreements = (): Agreements => ({ termsAgreed: false, privacyAgreed: false, marketingAgreed: false })
export const requiredAgreed = (a: Agreements) => a.termsAgreed && a.privacyAgreed

export function TermsAgreement({ field }: { field: { value: Agreements; onChange: (v: Agreements) => void; error?: string | null } }) {
  const { value, onChange, error } = field
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
              {item.doc && (
                <a
                  href={item.doc}
                  target="_blank"
                  rel="noreferrer"
                  onClick={(e) => e.stopPropagation()}
                  className="ml-1.5 text-xs font-medium text-gray-500 underline hover:text-gray-900 dark:hover:text-white"
                >
                  {t('terms.view')}
                </a>
              )}
            </>
          }
          checked={value[item.key]}
          onChange={(e) => onChange({ ...value, [item.key]: e.target.checked })}
        />
      ))}
      <p className="text-xs leading-relaxed text-gray-500 dark:text-gray-400">{t('terms.marketingNote')}</p>
      <FieldError>{error}</FieldError>
    </div>
  )
}
