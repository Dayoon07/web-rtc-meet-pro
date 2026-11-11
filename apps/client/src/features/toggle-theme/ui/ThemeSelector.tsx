import { Monitor, Moon, Sun } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { type ThemePreference, useThemeStore } from '@/shared/lib/theme'
import { Segmented } from '@/shared/ui'

/** 설정 화면용: 라이트 / 다크 / 시스템 */
export function ThemeSelector() {
  const { t } = useTranslation()
  const { preference, setPreference } = useThemeStore()
  const options: { value: ThemePreference; label: ReactNode }[] = [
    { value: 'light', label: <><Sun className="size-4" /> {t('theme.light')}</> },
    { value: 'dark', label: <><Moon className="size-4" /> {t('theme.dark')}</> },
    { value: 'system', label: <><Monitor className="size-4" /> {t('theme.system')}</> },
  ]
  return <Segmented name={t('settings.displayTitle')} value={preference} options={options} onChange={(v) => v && setPreference(v)} />
}
