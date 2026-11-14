import { useEffect } from 'react'
import { presenceApi } from '../api/presence-api'

const IDLE_MS = 5 * 60_000

export function useAwayTracker(enabled: boolean) {
  useEffect(() => {
    if (!enabled) return
    let away = false
    let timer = 0
    const set = (next: boolean) => {
      if (next === away) return
      away = next
      presenceApi.setAway(next).catch(() => {
        away = !next
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
