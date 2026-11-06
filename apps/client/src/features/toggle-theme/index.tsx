import { Monitor, Moon, Sun } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { type ThemePreference, useResolvedTheme, useThemeStore } from '@/shared/lib/theme'
import { IconButton, Segmented } from '@/shared/ui'

/** 헤더용: 해/달 아이콘 한 번 클릭으로 라이트 ↔ 다크 */
export function ThemeToggle() {
  const { t } = useTranslation()
  const resolved = useResolvedTheme()
  const setPreference = useThemeStore((s) => s.setPreference)
  const next = resolved === 'dark' ? 'light' : 'dark'
  return (
    <IconButton label={t(next === 'dark' ? 'theme.toDark' : 'theme.toLight')} onClick={() => setPreference(next)}>
      {resolved === 'dark' ? <Sun className="size-4.5" /> : <Moon className="size-4.5" />}
    </IconButton>
  )
}

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
