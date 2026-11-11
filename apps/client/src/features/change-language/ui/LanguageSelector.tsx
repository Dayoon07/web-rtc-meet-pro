import { Check } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { currentLanguage, LANGUAGES } from '@/shared/i18n'
import { cn } from '@/shared/lib/cn'
import { NATIVE_NAMES } from '../lib/change-language'

/** 설정 화면용: 4개 언어 버튼 */
export function LanguageSelector() {
  const { i18n } = useTranslation()
  const current = currentLanguage()
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
      {LANGUAGES.map((lng) => (
        <button
          key={lng}
          type="button"
          onClick={() => void i18n.changeLanguage(lng)}
          aria-pressed={lng === current}
          className={cn(
            'flex h-11 items-center justify-center gap-2 rounded-lg border text-sm font-medium transition',
            lng === current
              ? 'border-brand-500 bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-200'
              : 'border-gray-200 text-gray-700 hover:bg-gray-50 dark:border-gray-800 dark:text-gray-300 dark:hover:bg-gray-800',
          )}
        >
          {lng === current && <Check className="size-4" />}
          {NATIVE_NAMES[lng]}
        </button>
      ))}
    </div>
  )
}
