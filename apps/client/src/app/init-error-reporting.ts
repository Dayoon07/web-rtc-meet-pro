import { ApiError, reportClientError } from '@/shared/api'
import { newErrorCode } from '@/shared/lib/unexpected-error'

/** 한 번 열린 화면에서 보낼 최대 신고 수 (같은 오류가 반복돼도 서버를 두드리지 않게) */
const MAX_REPORTS = 10

/**
 * 화면 어디서든 잡히지 않은 오류를 서버 admin_error_logs 에 조용히 남긴다 (안내 창은 띄우지 않음).
 * - 라우트에서 난 렌더링 오류는 RouteErrorPage 가 따로 신고하고 화면에 에러 코드를 보여 준다
 * - 서버 응답 오류(ApiError)는 서버가 이미 기록했거나 http() 가 신고했다
 * - 브라우저 확장 프로그램·다른 출처 스크립트·ResizeObserver 경고는 우리 오류가 아니므로 뺀다
 */
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
