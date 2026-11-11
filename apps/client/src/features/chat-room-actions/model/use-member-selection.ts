import { useState } from 'react'
import type { MemberSelection } from './types'

/** 체크해서 고르는 팀원 목록의 상태 (고른 사람, 누르면 넣고 빼기, 비우기) */
export function useMemberSelection(): MemberSelection & { reset: () => void } {
  const [selected, setSelected] = useState<Set<number>>(new Set())
  return {
    selected,
    toggle: (id) =>
      setSelected((cur) => {
        const next = new Set(cur)
        if (next.has(id)) next.delete(id)
        else next.add(id)
        return next
      }),
    reset: () => setSelected(new Set()),
  }
}
