import { API_BASE_URL } from '@/shared/config/env'
import { translateDynamic } from '@/shared/i18n'

/** 서버 ErrorResponse { code, message, params, field } */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly params: Record<string, unknown>
  readonly field: string | null

  constructor(status: number, code: string, message: string, params: Record<string, unknown> = {}, field: string | null = null) {
    super(message)
    this.status = status
    this.code = code
    this.params = params
    this.field = field
  }
}

/**
 * shared 는 상위 레이어(entities/session)를 import 할 수 없으므로
 * 토큰 접근/갱신 로직은 app 레이어에서 configureHttp 로 주입한다.
 */
type AuthHooks = {
  getAccessToken: () => string | null
  refresh: () => Promise<boolean>
  onUnauthorized: () => void
}

let hooks: AuthHooks = {
  getAccessToken: () => null,
  refresh: async () => false,
  onUnauthorized: () => {},
}

export function configureHttp(next: AuthHooks) {
  hooks = next
}

/** 웹소켓·미디어 서버 접속 시 토큰이 필요할 때 */
export const getAccessToken = () => hooks.getAccessToken()
export const refreshAccessToken = () => hooks.refresh()

type HttpOptions = Omit<RequestInit, 'body'> & {
  json?: unknown
  /** multipart 업로드. Content-Type 은 브라우저가 boundary 와 함께 붙인다 */
  form?: FormData
  /** false 면 Authorization 헤더를 붙이지 않고 401 갱신도 하지 않음 */
  auth?: boolean
}

export async function http<T>(path: string, options: HttpOptions = {}, retried = false): Promise<T> {
  const { json, form, auth = true, headers, ...rest } = options
  const h = new Headers(headers)
  if (json !== undefined) h.set('Content-Type', 'application/json')
  const token = auth ? hooks.getAccessToken() : null
  if (token) h.set('Authorization', `Bearer ${token}`)

  let res: Response
  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      ...rest,
      headers: h,
      body: json !== undefined ? JSON.stringify(json) : form,
    })
  } catch {
    throw new ApiError(0, 'NETWORK', 'network error')
  }

  if (res.status === 401 && auth && !retried) {
    if (await hooks.refresh()) return http<T>(path, options, true)
    hooks.onUnauthorized()
  }

  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as
      | { code?: string; message?: string; params?: Record<string, unknown>; field?: string | null }
      | null
    throw new ApiError(res.status, body?.code ?? 'UNKNOWN', body?.message ?? res.statusText, body?.params ?? {}, body?.field ?? null)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

/** 서버 에러 코드 → 현재 언어 문구 (errors.<CODE>). 번역이 없으면 서버 메시지 */
export function errorMessage(error: unknown): string | null {
  if (!error) return null
  if (error instanceof ApiError) return translateDynamic(`errors.${error.code}`, error.params, error.message)
  return translateDynamic('errors.UNKNOWN')
}
