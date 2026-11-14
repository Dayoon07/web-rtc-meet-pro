import { API_BASE_URL } from '@/shared/config/env'
import { API } from './endpoints'
import { currentLanguage, translateDynamic } from '@/shared/i18n'
import { type ErrorReason, newErrorCode, showUnexpectedError, toErrorReason } from '@/shared/lib/unexpected-error'

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

  get unexpected(): boolean {
    return this.errorCode !== null
  }
}

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

export const getAccessToken = () => hooks.getAccessToken()
export const refreshAccessToken = () => hooks.refresh()

export type RequestOptions = Omit<RequestInit, 'body'> & {
  json?: unknown
  form?: FormData
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
    if (rest.signal?.aborted || (e instanceof DOMException && e.name === 'AbortError')) throw new ApiError(0, 'ABORTED', 'aborted')
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
  }
}

export function errorMessage(error: unknown): string | null {
  if (!error) return null
  if (error instanceof ApiError) {
    if (error.code === 'ABORTED') return null
    return translateDynamic(`errors.${error.code}`, error.params, error.message)
  }
  return translateDynamic('errors.UNKNOWN')
}
