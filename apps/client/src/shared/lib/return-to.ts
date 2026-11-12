import { DEFAULT_APP_ROUTE, safeRedirect } from '@/shared/config/routes'

/**
 * 로그인·회원가입·이메일 인증·프로필 입력·구글 로그인을 다 거친 뒤 돌아갈 주소 (예: 팀 초대 링크 /join/...).
 * 인증 메일 링크를 새 탭에서 열어도 이어지게 localStorage 에 둔다. 하루가 지나면 버린다.
 * 그 주소에 도착하면 지운다 (clearReturnToIfArrived).
 */
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

/** 돌아갈 주소를 기억 (이 사이트 안 경로만, 처음 화면이면 기억할 필요 없음) */
export function rememberReturnTo(path: string | null | undefined) {
  if (!path) return
  const safe = safeRedirect(path)
  if (safe === DEFAULT_APP_ROUTE) return
  try {
    localStorage.setItem(KEY, JSON.stringify({ path: safe, at: Date.now() } satisfies Stored))
  } catch {
    // 저장이 안 되면 처음 화면으로 간다
  }
}

/** 가입·인증을 마친 뒤 갈 곳: 기억해 둔 주소, 없으면 처음 화면 (지우지 않는다 — 도착했을 때 지운다) */
export function returnToPath(): string {
  return read()?.path ?? DEFAULT_APP_ROUTE
}

/** 기억해 둔 주소에 도착했으면 지운다 */
export function clearReturnToIfArrived(pathname: string) {
  const stored = read()
  if (!stored) return
  if (stored.path.split('?')[0] !== pathname) return
  try {
    localStorage.removeItem(KEY)
  } catch {
    // 지우지 못해도 하루 뒤에는 버려진다
  }
}
