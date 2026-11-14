import { sessionApi, useSessionStore } from '@/entities/session'
import { configureRequest } from '@/shared/api'

let refreshing: Promise<boolean> | null = null

export function initRequest() {
  configureRequest({
    getAccessToken: () => useSessionStore.getState().accessToken,
    refresh: () => {
      refreshing ??= (async () => {
        const { refreshToken, setTokens, clear } = useSessionStore.getState()
        if (!refreshToken) return false
        try {
          setTokens(await sessionApi.refresh(refreshToken))
          return true
        } catch {
          clear()
          return false
        } finally {
          refreshing = null
        }
      })()
      return refreshing
    },
    onUnauthorized: () => useSessionStore.getState().clear(),
  })
}
