import { create } from 'zustand'
import { persist } from 'zustand/middleware'

const API = '/api'

export type AuthTokens = { accessToken: string; refreshToken: string; expiresIn: number }

type SessionState = {
  accessToken: string | null
  refreshToken: string | null
  setTokens: (tokens: AuthTokens) => void
  clear: () => void
}

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

export async function http<T>(path: string, init: RequestInit & { json?: unknown; auth?: boolean } = {}, retried = false): Promise<T> {
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

export type ErrorSource = 'SERVER' | 'WEB' | 'APP'
export type ErrorCategory = 'DB_SCHEMA' | 'DB_CONSTRAINT' | 'DB_CONNECTION' | 'DB_ETC' | 'EXTERNAL' | 'CODE' | 'CLIENT' | 'NETWORK'

export type ErrorLog = {
  errorCode: string
  source: ErrorSource
  category: ErrorCategory
  exceptionClass: string | null
  message: string | null
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

export type AdminPayment = {
  orderId: string
  orderName: string
  amount: number
  status: 'READY' | 'DONE' | 'CANCELED' | 'FAILED' | 'ABORTED'
  paymentKind: 'PLAN' | 'OVERAGE'
  billingCycle: 'MONTHLY' | 'YEARLY' | null
  userId: number
  userEmail: string | null
  teamName: string | null
  approvedAt: string | null
  createdAt: string
  refundedAmount: number
  refundedAt: string | null
  refundReason: string | null
  hasPaymentKey: boolean
}

export type RefundQuote = {
  orderId: string
  orderName: string
  planName: string
  paidAmount: number
  refundable: boolean
  refundAmount: number
  rule: 'FULL_7_DAYS' | 'PRORATED' | 'NONE'
  usedDays: number
  periodDays: number
  blockedBy: string | null
  cancelsPlan: boolean
}

export type PendingSignup = {
  id: number
  email: string
  nickname: string
  realName: string | null
  hasPassword: boolean
  createdAt: string
  lastLoginAt: string | null
}

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
  pendingSignups: (q: string) => http<PendingSignup[]>(`/admin/signups/pending${q ? `?q=${encodeURIComponent(q)}` : ''}`),
  verifyEmail: (userId: number) => http<void>(`/admin/users/${userId}/verify-email`, { method: 'POST' }),
  rejectSignup: (userId: number) => http<void>(`/admin/users/${userId}/reject-signup`, { method: 'POST' }),
  payments: (q: string, status: string) => {
    const p = new URLSearchParams()
    if (q) p.set('q', q)
    if (status) p.set('status', status)
    return http<AdminPayment[]>(`/admin/payments?${p}`)
  },
  refundQuote: (orderId: string) => http<RefundQuote>(`/admin/payments/${encodeURIComponent(orderId)}/refund`),
  refund: (orderId: string, amount: number, reason: string) =>
    http<RefundQuote>(`/admin/payments/${encodeURIComponent(orderId)}/refund`, { method: 'POST', json: { amount, reason } }),
}

export async function downloadFile(path: string, fallbackName: string) {
  const send = () => fetch(`${API}${path}`, { headers: { Authorization: `Bearer ${useSession.getState().accessToken ?? ''}` } })
  let res = await send()
  if (res.status === 401 && (await refresh())) res = await send()
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null
    throw new ApiError(res.status, 'DOWNLOAD_FAILED', body?.message ?? '내려받지 못했어요.')
  }
  const name = /filename="?([^";]+)"?/.exec(res.headers.get('Content-Disposition') ?? '')?.[1] ?? fallbackName
  const url = URL.createObjectURL(await res.blob())
  const a = Object.assign(document.createElement('a'), { href: url, download: name })
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export const ERROR_CODE = /^ERR-\d{8}-[0-9A-F]{20}$/
