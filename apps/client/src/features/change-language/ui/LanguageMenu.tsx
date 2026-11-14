import { Check, Languages } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { currentLanguage, LANGUAGES } from '@/shared/i18n'
import { cn } from '@/shared/lib/cn'
import { Menu, MenuItem } from '@/shared/ui'
import { NATIVE_NAMES } from '../lib/change-language'

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
