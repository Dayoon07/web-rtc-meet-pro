import { API_BASE_URL } from '@/shared/config/env'
import { API } from './endpoints'
import { currentLanguage, translateDynamic } from '@/shared/i18n'
import { type ErrorReason, newErrorCode, showUnexpectedError, toErrorReason } from '@/shared/lib/unexpected-error'

/**
 * 서버 ErrorResponse { code, message, params, field, errorCode?, reason? }.
 * errorCode / reason 은 예상하지 못한 에러(서버 500, 서버 연결 실패)일 때만 있다 — 이때는 이미 안내 창을 띄웠다.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly params: Record<string, unknown>
  readonly field: string | null
  readonly errorCode: string | null
  readonly reason: ErrorReason | null

  constructor(
    status: number,
    code: string,
    message: string,
    params: Record<string, unknown> = {},
    field: string | null = null,
    errorCode: string | null = null,
    reason: ErrorReason | null = null,
  ) {
    super(message)
    this.status = status
    this.code = code
    this.params = params
    this.field = field
    this.errorCode = errorCode
    this.reason = reason
  }

  /** 에러 코드와 함께 안내 창으로 보여 준 에러 (토스트를 또 띄우지 않는다) */
  get unexpected(): boolean {
    return this.errorCode !== null
  }
}

/**
 * shared 는 상위 레이어(entities/session)를 import 할 수 없으므로
 * 토큰 접근/갱신 로직은 app 레이어에서 configureRequest 로 주입한다.
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

export function configureRequest(next: AuthHooks) {
  hooks = next
}

/** 웹소켓·미디어 서버 접속 시 토큰이 필요할 때 */
export const getAccessToken = () => hooks.getAccessToken()
export const refreshAccessToken = () => hooks.refresh()

export type RequestOptions = Omit<RequestInit, 'body'> & {
  json?: unknown
  /** multipart 업로드. Content-Type 은 브라우저가 boundary 와 함께 붙인다 */
  form?: FormData
  /** false 면 Authorization 헤더를 붙이지 않고 401 갱신도 하지 않음 */
  auth?: boolean
}

type ErrorBody = {
  code?: string
  message?: string
  params?: Record<string, unknown>
  field?: string | null
  errorCode?: string | null
  reason?: string | null
}

export async function request<T>(path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  const { json, form, auth = true, headers, ...rest } = options
  const h = new Headers(headers)
  if (json !== undefined) h.set('Content-Type', 'application/json')
  // 서버가 보내는 메일(초대, 비밀번호 재설정)을 화면과 같은 언어로 쓰게
  if (!h.has('Accept-Language')) h.set('Accept-Language', currentLanguage())
  const token = auth ? hooks.getAccessToken() : null
  if (token) h.set('Authorization', `Bearer ${token}`)

  let res: Response
  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      ...rest,
      headers: h,
      body: json !== undefined ? JSON.stringify(json) : form,
    })
  } catch (e) {
    // 화면을 떠나며 요청을 취소한 경우는 오류가 아니다
    if (rest.signal?.aborted || (e instanceof DOMException && e.name === 'AbortError')) throw new ApiError(0, 'ABORTED', 'aborted')
    // 브라우저가 오프라인이면 사용자도 이미 안다. 안내 창은 온라인인데 서버에 닿지 못할 때만
    if (!navigator.onLine) throw new ApiError(0, 'NETWORK', 'network error')
    throw unreachable(0, rest.method ?? 'GET', path, e)
  }

  if (res.status === 401 && auth && !retried) {
    if (await hooks.refresh()) return request<T>(path, options, true)
    hooks.onUnauthorized()
  }

  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as ErrorBody | null
    if (res.status >= 500) {
      // 우리 서버가 만든 응답이면 서버가 이미 에러 코드를 만들고 기록했다.
      // 아니면(프록시의 502/503/504 등) 서버까지 닿지 못한 것 → 화면에서 코드를 만들고 신고한다
      if (typeof body?.errorCode !== 'string') throw unreachable(res.status, rest.method ?? 'GET', path)
      const error = new ApiError(
        res.status,
        body.code ?? 'INTERNAL_ERROR',
        body.message ?? res.statusText,
        body.params ?? {},
        body.field ?? null,
        body.errorCode,
        toErrorReason(body.reason),
      )
      showUnexpectedError({ errorCode: body.errorCode, reason: error.reason! })
      throw error
    }
    throw new ApiError(res.status, body?.code ?? 'UNKNOWN', body?.message ?? res.statusText, body?.params ?? {}, body?.field ?? null)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

/** 서버에 닿지 못함: 에러 코드를 만들어 안내 창을 띄우고, 서버가 살아 있으면 기록되도록 신고한다 */
function unreachable(status: number, method: string, path: string, cause?: unknown): ApiError {
  const errorCode = newErrorCode()
  showUnexpectedError({ errorCode, reason: 'NETWORK' })
  void reportClientError({
    errorCode,
    category: 'NETWORK',
    name: status ? `HTTP ${status}` : 'FetchError',
    message: `${method} ${path.split('?')[0]} — ${cause instanceof Error ? cause.message : status || 'failed'}`,
  })
  return new ApiError(status, 'NETWORK', 'network error', {}, null, errorCode, 'NETWORK')
}

export type ClientErrorReport = {
  errorCode: string
  category: 'CLIENT' | 'NETWORK'
  name?: string
  message?: string
  stack?: string
}

/**
 * 화면 오류를 서버 admin_error_logs 에 남긴다 (POST /api/client-errors). 실패해도 조용히 넘어간다.
 * 로그인 중이면 토큰을 붙여 사용자 id 도 남기고, 토큰이 만료돼 401 이면 토큰 없이 다시 보낸다.
 */
export async function reportClientError(report: ClientErrorReport): Promise<void> {
  const body = JSON.stringify({
    ...report,
    source: 'WEB',
    message: report.message?.slice(0, 4000),
    stack: report.stack?.slice(0, 32000),
    name: report.name?.slice(0, 300),
    path: window.location.pathname.slice(0, 1000),
  })
  const send = (token: string | null) =>
    fetch(`${API_BASE_URL}${API.clientErrors}`, {
      method: 'POST',
      keepalive: true,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body,
    })
  try {
    const token = hooks.getAccessToken()
    const res = await send(token)
    if (res.status === 401 && token) await send(null)
  } catch {
    // 서버에 연결이 안 되면 기록할 방법이 없다 (화면에는 에러 코드를 이미 보여 줬다)
  }
}

/** 서버 에러 코드 → 현재 언어 문구 (errors.<CODE>). 번역이 없으면 서버 메시지 */
export function errorMessage(error: unknown): string | null {
  if (!error) return null
  if (error instanceof ApiError) {
    if (error.code === 'ABORTED') return null
    return translateDynamic(`errors.${error.code}`, error.params, error.message)
  }
  return translateDynamic('errors.UNKNOWN')
}
