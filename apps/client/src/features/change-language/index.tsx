import { Check, Languages } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { currentLanguage, type Language, LANGUAGES } from '@/shared/i18n'
import { cn } from '@/shared/lib/cn'
import { Menu, MenuItem } from '@/shared/ui'

/** 각 언어 이름은 그 언어로 표시 (사용자가 못 읽는 언어로 바뀌어도 되돌릴 수 있게) */
const NATIVE_NAMES: Record<Language, string> = { ko: '한국어', en: 'English', ja: '日本語', zh: '简体中文' }

/** 헤더용: 지구본 아이콘 → 언어 메뉴 */
export function LanguageMenu({ align = 'end' }: { align?: 'start' | 'end' }) {
  const { t, i18n } = useTranslation()
  const current = currentLanguage()
  return (
    <Menu
      label={t('lang.label')}
      align={align}
      trigger={({ open, toggle, id }) => (
        <button
          type="button"
          onClick={toggle}
          aria-expanded={open}
          aria-controls={id}
          aria-haspopup="menu"
          aria-label={t('lang.label')}
          title={t('lang.label')}
          className="inline-flex h-9 items-center gap-1.5 rounded-lg px-2 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-white"
        >
          <Languages className="size-4.5" />
          <span className="uppercase">{current}</span>
        </button>
      )}
    >
      {(close) =>
        LANGUAGES.map((lng) => (
          <MenuItem
            key={lng}
            icon={<Check className={cn(lng === current ? 'opacity-100' : 'opacity-0')} />}
            onClick={() => {
              void i18n.changeLanguage(lng)
              close()
            }}
          >
            {NATIVE_NAMES[lng]}
          </MenuItem>
        ))
      }
    </Menu>
  )
}

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
