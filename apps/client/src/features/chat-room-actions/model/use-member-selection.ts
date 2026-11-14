import { useState } from 'react'
import type { MemberSelection } from './types'

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
