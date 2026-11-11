import { useEffect } from 'react'
import { presenceApi } from '../api/presence-api'

/** 이만큼 움직임이 없으면 자리 비움 */
const IDLE_MS = 5 * 60_000

/**
 * 창을 안 보거나(다른 탭·최소화) 5분 동안 마우스·키보드가 없으면 "자리 비움", 돌아오면 "접속 중" 을 서버에 알린다.
 * 로그인한 동안 앱에서 한 번
 */
export function useAwayTracker(enabled: boolean) {
  useEffect(() => {
    if (!enabled) return
    let away = false
    let timer = 0
    const set = (next: boolean) => {
      if (next === away) return
      away = next
      presenceApi.setAway(next).catch(() => {
        away = !next // 실패하면 다음 움직임 때 다시
      })
    }
    const active = () => {
      window.clearTimeout(timer)
      if (document.visibilityState === 'visible') set(false)
      timer = window.setTimeout(() => set(true), IDLE_MS)
    }
    const onVisibility = () => (document.visibilityState === 'visible' ? active() : set(true))
    const events = ['pointermove', 'pointerdown', 'keydown', 'wheel', 'touchstart'] as const
    events.forEach((e) => window.addEventListener(e, active, { passive: true }))
    document.addEventListener('visibilitychange', onVisibility)
    active()
    return () => {
      window.clearTimeout(timer)
      events.forEach((e) => window.removeEventListener(e, active))
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [enabled])
}
