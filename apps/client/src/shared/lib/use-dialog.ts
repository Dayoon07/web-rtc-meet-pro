import { useState } from 'react'

export type Dialog<D = undefined, R = void> = {
  open: boolean
  data: D | undefined
  close: () => void
  done: (result: R) => void
}

export type DialogControl<D = undefined, R = void> = Dialog<D, R> & {
  show: (data?: D) => void
}

export function useDialog<D = undefined, R = void>(onDone?: (result: R, data: D | undefined) => void): DialogControl<D, R> {
  const [state, setState] = useState<{ open: boolean; data: D | undefined }>({ open: false, data: undefined })
  const close = () => setState((s) => ({ ...s, open: false }))
  return {
    open: state.open,
    data: state.data,
    show: (data) => setState({ open: true, data }),
    close,
    done: (result) => {
      close()
      onDone?.(result, state.data)
    },
  }
}
