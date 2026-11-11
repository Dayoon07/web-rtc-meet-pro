import { sessionApi, useSessionStore } from '@/entities/session'
import { configureRequest } from '@/shared/api'

/** 동시에 여러 요청이 401 을 받아도 refresh 는 한 번만 */
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
