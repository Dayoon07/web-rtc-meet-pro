import { create } from 'zustand'

/** 대략적인 원인. 서버 admin_error_logs.category 와 같은 값 */
export type ErrorReason = 'DB_SCHEMA' | 'DB_CONSTRAINT' | 'DB_CONNECTION' | 'DB_ETC' | 'EXTERNAL' | 'CODE' | 'CLIENT' | 'NETWORK'

const REASONS: readonly ErrorReason[] = ['DB_SCHEMA', 'DB_CONSTRAINT', 'DB_CONNECTION', 'DB_ETC', 'EXTERNAL', 'CODE', 'CLIENT', 'NETWORK']

export const toErrorReason = (value: unknown): ErrorReason =>
  REASONS.includes(value as ErrorReason) ? (value as ErrorReason) : 'CODE'

/** 예상하지 못한 에러 1건: 사용자가 문의할 때 알려 줄 에러 코드 + 원인 */
export type UnexpectedError = { errorCode: string; reason: ErrorReason }

/** ERR-YYYYMMDD-(UUID 앞 20자리, 대문자). 서버 AdminErrorLog.newErrorCode 와 같은 형식 */
export function newErrorCode(now = new Date()): string {
  const ymd = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
  return `ERR-${ymd}-${randomHex(20).toUpperCase()}`
}

function randomHex(length: number): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID().replaceAll('-', '').slice(0, length)
  const bytes = crypto.getRandomValues(new Uint8Array(Math.ceil(length / 2)))
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('').slice(0, length)
}

/** 연결 실패 안내를 닫은 뒤 이 시간 동안은 같은 안내를 다시 띄우지 않는다 (서버가 꺼져 있을 때 계속 뜨지 않게) */
const NETWORK_QUIET_MS = 60_000

type State = {
  current: UnexpectedError | null
  networkQuietUntil: number
  show: (error: UnexpectedError) => void
  close: () => void
}

/** 화면 전체에 하나만 뜨는 "예상하지 못한 에러" 안내 창 상태 (이미 떠 있으면 새 에러는 무시) */
export const useUnexpectedErrorStore = create<State>((set, get) => ({
  current: null,
  networkQuietUntil: 0,
  show: (error) => {
    const { current, networkQuietUntil } = get()
    if (current) return
    if (error.reason === 'NETWORK' && Date.now() < networkQuietUntil) return
    set({ current: error })
  },
  close: () => {
    const closing = get().current
    set({ current: null, networkQuietUntil: closing?.reason === 'NETWORK' ? Date.now() + NETWORK_QUIET_MS : get().networkQuietUntil })
  },
}))

export const showUnexpectedError = (error: UnexpectedError) => useUnexpectedErrorStore.getState().show(error)
