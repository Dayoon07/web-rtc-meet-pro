import { ApiError, reportClientError } from '@/shared/api'
import { newErrorCode } from '@/shared/lib/unexpected-error'

const MAX_REPORTS = 10

export function initErrorReporting() {
  const seen = new Set<string>()

  const send = (name: string, message: string, stack?: string) => {
    const key = `${name}:${message}`
    if (seen.size >= MAX_REPORTS || seen.has(key)) return
    seen.add(key)
    void reportClientError({ errorCode: newErrorCode(), category: 'CLIENT', name, message, stack })
  }

  window.addEventListener('error', (e) => {
    if (!e.error && /^(Script error|ResizeObserver loop)/i.test(e.message)) return
    if (e.filename && !e.filename.startsWith(window.location.origin)) return
    const err = e.error instanceof Error ? e.error : null
    send(err?.name ?? 'Error', err?.message ?? e.message, err?.stack)
  })

  window.addEventListener('unhandledrejection', (e) => {
    const reason: unknown = e.reason
    if (reason instanceof ApiError) return
    if (reason instanceof DOMException && reason.name === 'AbortError') return
    if (reason instanceof Error) send(reason.name, reason.message, reason.stack)
    else send('UnhandledRejection', String(reason))
  })
}
