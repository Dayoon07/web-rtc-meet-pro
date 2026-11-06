import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type AuthTokens = {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

type SessionState = {
  accessToken: string | null
  refreshToken: string | null
  setTokens: (tokens: AuthTokens) => void
  clear: () => void
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      setTokens: ({ accessToken, refreshToken }) => set({ accessToken, refreshToken }),
      clear: () => set({ accessToken: null, refreshToken: null }),
    }),
    { name: 'wrmp-session' },
  ),
)

export const useIsAuthenticated = () => useSessionStore((s) => s.accessToken !== null)
