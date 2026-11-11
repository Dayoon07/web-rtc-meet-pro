import { useState } from 'react'

/**
 * 창(모달) 하나를 여닫는 묶음. 창 컴포넌트에는 open·onClose·onDone 를 따로 넘기지 않고 이것 하나만 넘긴다.
 *   data  : 창을 열 때 함께 준 값 (예: 고칠 카테고리, 미리 고를 부서)
 *   done  : 창에서 일을 마쳤을 때 (닫고 onDone 을 부른다)
 */
export type Dialog<D = undefined, R = void> = {
  open: boolean
  data: D | undefined
  close: () => void
  done: (result: R) => void
}

export type DialogControl<D = undefined, R = void> = Dialog<D, R> & {
  /** 창 열기 (data 를 함께 줄 수 있다) */
  show: (data?: D) => void
}

/** onDone(결과, 열 때 준 data) */
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
