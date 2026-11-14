import { useEffect, useSyncExternalStore } from 'react'
import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type ThemePreference = 'light' | 'dark' | 'system'

type ThemeState = {
  preference: ThemePreference
  setPreference: (preference: ThemePreference) => void
}

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

export function useResolvedTheme(): 'light' | 'dark' {
  const preference = useThemeStore((s) => s.preference)
  const systemDark = useSystemDark()
  return preference === 'system' ? (systemDark ? 'dark' : 'light') : preference
}

export function useApplyTheme() {
  const resolved = useResolvedTheme()
  useEffect(() => {
    document.documentElement.classList.toggle('dark', resolved === 'dark')
  }, [resolved])
  return resolved
}
