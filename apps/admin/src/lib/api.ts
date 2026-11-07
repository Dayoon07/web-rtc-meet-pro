import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * 관리자 사이트 API. 서비스와 같은 Spring 서버를 쓴다.
 * - 로그인/토큰 갱신: /api/auth (일반 회원 로그인과 같다)
 * - 관리 기능: /api/admin (users.system_role = ADMIN 인 계정만. 서버가 매 요청 DB 에서 확인)
 */
const API = '/api'

export type AuthTokens = { accessToken: string; refreshToken: string; expiresIn: number }

type SessionState = {
  accessToken: string | null
  refreshToken: string | null
  setTokens: (tokens: AuthTokens) => void
  clear: () => void
}

/** 서비스 웹과 다른 저장 키 (운영에서는 출처도 다르다) */
export const useSession = create<SessionState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      setTokens: ({ accessToken, refreshToken }) => set({ accessToken, refreshToken }),
      clear: () => set({ accessToken: null, refreshToken: null }),
    }),
    { name: 'limeet-admin-session' },
  ),
)

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

/** 동시에 여러 요청이 401 을 받아도 토큰 갱신은 한 번만 */
let refreshing: Promise<boolean> | null = null

function refresh(): Promise<boolean> {
  refreshing ??= (async () => {
    const { refreshToken, setTokens, clear } = useSession.getState()
    if (!refreshToken) return false
    try {
      const res = await fetch(`${API}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
      if (!res.ok) throw new Error('refresh failed')
      setTokens((await res.json()) as AuthTokens)
      return true
    } catch {
      clear()
      return false
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

/** auth: false 면 토큰을 붙이지 않는다 (로그인·로그아웃 — 만료된 토큰이 남아 있으면 서버가 401 로 막는다) */
async function http<T>(path: string, init: RequestInit & { json?: unknown; auth?: boolean } = {}, retried = false): Promise<T> {
  const { json, headers, auth = true, ...rest } = init
  const h = new Headers(headers)
  if (json !== undefined) h.set('Content-Type', 'application/json')
  const token = auth ? useSession.getState().accessToken : null
  if (token) h.set('Authorization', `Bearer ${token}`)

  let res: Response
  try {
    res = await fetch(`${API}${path}`, { ...rest, headers: h, body: json !== undefined ? JSON.stringify(json) : undefined })
  } catch {
    throw new ApiError(0, 'NETWORK', '서버에 연결할 수 없어요.')
  }
  if (res.status === 401 && token && !retried) {
    if (await refresh()) return http<T>(path, init, true)
    useSession.getState().clear()
  }
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { code?: string; message?: string; errorCode?: string } | null
    const message = body?.errorCode ? `${body.message ?? '서버 오류'} (에러 코드 ${body.errorCode})` : (body?.message ?? res.statusText)
    throw new ApiError(res.status, body?.code ?? 'UNKNOWN', message)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

// ------------------------------------------------------------------ 타입 (서버 AdminErrorLogView 와 같다)

export type ErrorSource = 'SERVER' | 'WEB' | 'APP'
export type ErrorCategory = 'DB_SCHEMA' | 'DB_CONSTRAINT' | 'DB_CONNECTION' | 'DB_ETC' | 'EXTERNAL' | 'CODE' | 'CLIENT' | 'NETWORK'

export type ErrorLog = {
  errorCode: string
  source: ErrorSource
  category: ErrorCategory
  exceptionClass: string | null
  message: string | null
  /** 한 건 조회에서만 채워진다 */
  stackTrace: string | null
  httpMethod: string | null
  requestPath: string | null
  userId: number | null
  ipAddress: string | null
  userAgent: string | null
  resolved: boolean
  createdAt: string
  editedBy: number | null
  editedAt: string
}

export type AdminMe = { id: number; email: string; nickname: string }

export const adminApi = {
  login: async (email: string, password: string) => {
    const tokens = await http<AuthTokens>('/auth/login', { method: 'POST', json: { email, password }, auth: false })
    useSession.getState().setTokens(tokens)
    return tokens
  },
  logout: async () => {
    const { refreshToken, clear } = useSession.getState()
    clear()
    if (refreshToken) await http<void>('/auth/logout', { method: 'POST', json: { refreshToken }, auth: false }).catch(() => {})
  },
  me: () => http<AdminMe>('/admin/me'),
  errorLogs: (unresolved: boolean) => http<ErrorLog[]>(`/admin/error-logs?unresolved=${unresolved}`),
  errorLog: (code: string) => http<ErrorLog>(`/admin/error-logs/${encodeURIComponent(code)}`),
  resolve: (code: string) => http<ErrorLog>(`/admin/error-logs/${encodeURIComponent(code)}/resolve`, { method: 'POST' }),
}

/** 에러 코드 형식: ERR-YYYYMMDD-(대문자 16진수 20자리) */
export const ERROR_CODE = /^ERR-\d{8}-[0-9A-F]{20}$/
