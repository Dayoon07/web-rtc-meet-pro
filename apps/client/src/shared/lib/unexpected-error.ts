import { create } from 'zustand'

export type ErrorReason = 'DB_SCHEMA' | 'DB_CONSTRAINT' | 'DB_CONNECTION' | 'DB_ETC' | 'EXTERNAL' | 'CODE' | 'CLIENT' | 'NETWORK'

const REASONS: readonly ErrorReason[] = ['DB_SCHEMA', 'DB_CONSTRAINT', 'DB_CONNECTION', 'DB_ETC', 'EXTERNAL', 'CODE', 'CLIENT', 'NETWORK']

export const toErrorReason = (value: unknown): ErrorReason =>
  REASONS.includes(value as ErrorReason) ? (value as ErrorReason) : 'CODE'

export type UnexpectedError = { errorCode: string; reason: ErrorReason }

export function newErrorCode(now = new Date()): string {
  const ymd = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
  return `ERR-${ymd}-${randomHex(20).toUpperCase()}`
}

function randomHex(length: number): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID().replaceAll('-', '').slice(0, length)
  const bytes = crypto.getRandomValues(new Uint8Array(Math.ceil(length / 2)))
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('').slice(0, length)
}

const NETWORK_QUIET_MS = 60_000

type State = {
  current: UnexpectedError | null
  networkQuietUntil: number
  show: (error: UnexpectedError) => void
  close: () => void
}

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
