import { Moon, Sun } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useResolvedTheme, useThemeStore } from '@/shared/lib/theme'
import { IconButton } from '@/shared/ui'

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
