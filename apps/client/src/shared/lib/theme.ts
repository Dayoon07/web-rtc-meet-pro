import { useEffect, useSyncExternalStore } from 'react'
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type ThemePreference = 'light' | 'dark' | 'system'

type ThemeState = {
  preference: ThemePreference
  setPreference: (preference: ThemePreference) => void
}

/** index.html 의 초기 스크립트도 같은 키('wrmp-theme')를 읽는다 */
export const useThemeStore = create<ThemeState>()(
  persist((set) => ({ preference: 'system', setPreference: (preference) => set({ preference }) }), {
    name: 'wrmp-theme',
  }),
)

const media = () => window.matchMedia('(prefers-color-scheme: dark)')

function useSystemDark() {
  return useSyncExternalStore(
    (onChange) => {
      const m = media()
      m.addEventListener('change', onChange)
      return () => m.removeEventListener('change', onChange)
    },
    () => media().matches,
  )
}

/** 실제로 적용되는 테마 (system 이면 OS 설정을 따른다) */
export function useResolvedTheme(): 'light' | 'dark' {
  const preference = useThemeStore((s) => s.preference)
  const systemDark = useSystemDark()
  return preference === 'system' ? (systemDark ? 'dark' : 'light') : preference
}

/** app 레이어에서 한 번 호출: <html class="dark"> 동기화 */
export function useApplyTheme() {
  const resolved = useResolvedTheme()
  useEffect(() => {
    document.documentElement.classList.toggle('dark', resolved === 'dark')
  }, [resolved])
  return resolved
}
