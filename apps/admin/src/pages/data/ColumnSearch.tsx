import { Columns3 } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import type { TableColumn } from '@/lib/table-api'
import { Button, cx, inputClass } from '@/ui'

/** contains = 포함 (대소문자 무시, 숫자·날짜도 글자로), equals = 같음, empty = 비어 있음(NULL) */
export type ColumnMatch = 'contains' | 'equals' | 'empty'

const MATCHES: [ColumnMatch, string][] = [
  ['contains', '포함'],
  ['equals', '같음'],
  ['empty', '비어 있음'],
]

/** 컬럼 이름 옆 설명 (주석이 있으면 주석) */
const columnLabel = (c: TableColumn) => (c.comment ? `${c.name} — ${c.comment}` : c.name)

/**
 * 특정 컬럼에서 값 찾기: 컬럼 고르기 · 찾는 방식 · 값 → 조회. 조건은 주소에 쌓여서 여러 개를 함께 걸 수 있다.
 * onSearch(컬럼, 방식, 값)
 */
export function ColumnSearch({ columns, onSearch }: { columns: TableColumn[]; onSearch: (column: string, match: ColumnMatch, value: string) => void }) {
  // 비밀값·파일 컬럼은 못 찾는다
  const searchable = columns.filter((c) => !c.secret && c.kind !== 'BINARY')
  const [column, setColumn] = useState(searchable[0]?.name ?? '')
  const [match, setMatch] = useState<ColumnMatch>('contains')
  const [value, setValue] = useState('')
  const col = searchable.find((c) => c.name === column)
  // 긴 글은 "같음" 으로는 못 찾는다 (서버도 막는다)
  const matches = MATCHES.filter(([m]) => !(m === 'equals' && col?.kind === 'LONG_TEXT'))
  const pickOption = match === 'equals' && !!col?.options.length

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!column) return
    if (match !== 'empty' && !value.trim()) return
    onSearch(column, match, match === 'empty' ? '' : value.trim())
    setValue('')
  }

  if (!searchable.length) return null
  return (
    <form onSubmit={submit} className="flex flex-col gap-2 lg:flex-row lg:items-center">
      <span className="flex shrink-0 items-center gap-1.5 text-sm font-medium text-gray-600 dark:text-gray-300">
        <Columns3 className="size-4" />
        컬럼 검색
      </span>
      <select
        aria-label="컬럼"
        value={column}
        onChange={(e) => {
          setColumn(e.target.value)
          setValue('')
        }}
        className={cx(inputClass, 'lg:w-72')}
      >
        {searchable.map((c) => (
          <option key={c.name} value={c.name}>
            {columnLabel(c)}
          </option>
        ))}
      </select>
      <select aria-label="찾는 방식" value={match} onChange={(e) => setMatch(e.target.value as ColumnMatch)} className={cx(inputClass, 'lg:w-32')}>
        {matches.map(([m, label]) => (
          <option key={m} value={m}>
            {label}
          </option>
        ))}
      </select>
      {match !== 'empty' &&
        (pickOption ? (
          <select aria-label="값" value={value} onChange={(e) => setValue(e.target.value)} className={cx(inputClass, 'lg:flex-1')}>
            <option value="">값 고르기</option>
            {col!.options.map((o) => (
              <option key={o} value={o}>
                {o}
              </option>
            ))}
          </select>
        ) : (
          <input
            aria-label="찾을 값"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder={col?.kind === 'DATETIME' ? (match === 'contains' ? '예: 2026-11-12 또는 2026-11' : '예: 2026-11-12') : '찾을 값'}
            className={cx(inputClass, 'lg:flex-1')}
          />
        ))}
      <Button type="submit" className="h-10 shrink-0" disabled={match !== 'empty' && !value.trim()}>
        조회
      </Button>
    </form>
  )
}
