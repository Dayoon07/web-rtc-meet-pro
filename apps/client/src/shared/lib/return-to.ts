import { DEFAULT_APP_ROUTE, safeRedirect } from '@/shared/config/routes'

const KEY = 'mq.returnTo'
const MAX_AGE_MS = 24 * 60 * 60 * 1000

type Stored = { path: string; at: number }

function read(): Stored | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const value = JSON.parse(raw) as Stored
    if (typeof value.path !== 'string' || Date.now() - value.at > MAX_AGE_MS) return null
    return value
  } catch {
    return null
  }
}

export function rememberReturnTo(path: string | null | undefined) {
  if (!path) return
  const safe = safeRedirect(path)
  if (safe === DEFAULT_APP_ROUTE) return
  try {
    localStorage.setItem(KEY, JSON.stringify({ path: safe, at: Date.now() } satisfies Stored))
  } catch {
  }
}

export function returnToPath(): string {
  return read()?.path ?? DEFAULT_APP_ROUTE
}

export function clearReturnToIfArrived(pathname: string) {
  const stored = read()
  if (!stored) return
  if (stored.path.split('?')[0] !== pathname) return
  try {
    localStorage.removeItem(KEY)
  } catch {
  }
}
