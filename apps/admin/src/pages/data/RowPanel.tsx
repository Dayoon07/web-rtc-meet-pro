import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight, ExternalLink, History, Lock, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { type ColumnRef, fromLocalInput, type Row, type TableColumn, type TableMeta, tableApi, tableKeys, toLocalInput } from '@/lib/table-api'
import { Button, cx, inputClass, Spinner } from '@/ui'
import { refLink } from './cells'

type Draft = Record<string, string>

function toDraft(meta: TableMeta, row: Row | null): Draft {
  const d: Draft = {}
  meta.columns.forEach((c) => {
    const v = row?.[c.name] ?? null
    d[c.name] = c.kind === 'DATETIME' ? toLocalInput(v as string | null) : v === null ? '' : String(v)
  })
  return d
}

function toValue(col: TableColumn, text: string): unknown {
  if (text === '') return null
  if (col.kind === 'DATETIME') return fromLocalInput(text)
  return text
}

export function RowPanel({
  meta,
  rowId,
  historyTable,
  onClose,
  onSaved,
}: {
  meta: TableMeta
  rowId: string
  historyTable: string | null
  onClose: () => void
  onSaved: (row: Row) => void
}) {
  const queryClient = useQueryClient()
  const isNew = rowId === 'new'
  const row = useQuery({ queryKey: tableKeys.row(meta.name, rowId), queryFn: () => tableApi.row(meta.name, rowId), enabled: !isNew })
  const initial = useMemo(() => toDraft(meta, isNew ? null : (row.data ?? null)), [meta, isNew, row.data])
  const [draft, setDraft] = useState<Draft>(initial)
  useEffect(() => setDraft(initial), [initial])

  const changed = meta.columns.filter((c) => c.editable && draft[c.name] !== initial[c.name])
  const save = useMutation({
    mutationFn: () => {
      const values = Object.fromEntries(changed.map((c) => [c.name, toValue(c, draft[c.name])]))
      return isNew ? tableApi.insert(meta.name, values) : tableApi.update(meta.name, rowId, values)
    },
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: tableKeys.rows(meta.name) })
      void queryClient.invalidateQueries({ queryKey: tableKeys.list })
      if (meta.primaryKey) queryClient.setQueryData(tableKeys.row(meta.name, String(saved[meta.primaryKey])), saved)
      toast.success(isNew ? '새 행을 추가했어요.' : `${changed.length}개 칸을 저장했어요.`)
      onSaved(saved)
    },
  })

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const pk = meta.primaryKey

  return (
    <div className="fixed inset-0 z-30 flex justify-end">
      <button type="button" aria-label="닫기" className="absolute inset-0 bg-gray-950/30" onClick={onClose} />
      <aside className="relative flex h-full w-full max-w-xl flex-col bg-white shadow-2xl dark:bg-gray-900">
        <header className="flex items-center gap-3 border-b border-gray-200 px-5 py-3.5 dark:border-gray-800">
          <div className="min-w-0 flex-1">
            <p className="text-xs text-gray-500">{meta.label}</p>
            <h2 className="truncate font-semibold">
              {isNew ? '새 행 추가' : (
                <>
                  {pk} = <span className="font-mono">{rowId}</span>
                </>
              )}
            </h2>
          </div>
          <button type="button" onClick={onClose} aria-label="닫기" className="flex size-9 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800">
            <X className="size-5" />
          </button>
        </header>

        <div className="flex-1 overflow-y-auto px-5 py-4">
          {!isNew && row.isPending ? (
            <div className="flex justify-center p-10">
              <Spinner />
            </div>
          ) : !isNew && row.isError ? (
            <p className="rounded-xl bg-red-50 p-4 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300">{row.error.message}</p>
          ) : (
            <div className="flex flex-col gap-4">
              {!isNew && pk && historyTable && historyTable !== meta.name && (
                <Link
                  to={`/data/${historyTable}?filter=table_name:${meta.name}&filter=row_id:${encodeURIComponent(rowId)}`}
                  className="inline-flex h-8 items-center gap-1.5 self-start rounded-lg border border-gray-200 px-2.5 text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-200 dark:hover:bg-gray-800"
                >
                  <History className="size-3.5" />이 행의 변경 기록
                </Link>
              )}
              {!isNew && pk && <RelatedLinks refs={meta.columns.find((c) => c.name === pk)?.referencedBy ?? []} rowId={rowId} />}

              {meta.columns.map((c) => (
                <FieldRow
                  key={c.name}
                  col={c}
                  value={draft[c.name] ?? ''}
                  original={row.data?.[c.name] ?? null}
                  isNew={isNew}
                  changed={draft[c.name] !== initial[c.name]}
                  onChange={(v) => setDraft((d) => ({ ...d, [c.name]: v }))}
                />
              ))}
            </div>
          )}
        </div>

        <footer className="flex flex-col gap-2 border-t border-gray-200 px-5 py-3.5 dark:border-gray-800">
          {save.error && <p className="text-sm text-red-600 dark:text-red-400">{save.error.message}</p>}
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500">{meta.readOnly ? '보기 전용 테이블이에요.' : changed.length ? `${changed.length}개 칸을 바꿨어요.` : '바꾼 칸이 없어요.'}</span>
            <Button variant="outline" className="ml-auto" onClick={() => setDraft(initial)} disabled={!changed.length || save.isPending}>
              되돌리기
            </Button>
            <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!changed.length || meta.readOnly}>
              {isNew ? '추가' : '저장'}
            </Button>
          </div>
        </footer>
      </aside>
    </div>
  )
}

function RelatedLinks({ refs, rowId }: { refs: ColumnRef[]; rowId: string }) {
  if (!refs.length) return null
  return (
    <details className="group rounded-lg border border-gray-200 dark:border-gray-700">
      <summary className="flex h-9 cursor-pointer list-none items-center gap-1.5 px-3 text-xs font-medium text-gray-700 select-none dark:text-gray-200">
        <ChevronRight className="size-3.5 transition-transform group-open:rotate-90" />이 행을 가리키는 데이터 ({refs.length})
      </summary>
      <div className="flex flex-wrap gap-1.5 border-t border-gray-100 p-2.5 dark:border-gray-800">
        {refs.map((r) => (
          <Link
            key={`${r.table}.${r.column}`}
            to={`/data/${r.table}?filter=${r.column}:${encodeURIComponent(rowId)}`}
            className="inline-flex h-7 items-center gap-1 rounded-md bg-gray-50 px-2 font-mono text-xs text-gray-700 hover:bg-brand-50 hover:text-brand-700 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-brand-500/15"
          >
            <ExternalLink className="size-3" />
            {r.table}.{r.column}
          </Link>
        ))}
      </div>
    </details>
  )
}

function FieldRow({
  col,
  value,
  original,
  isNew,
  changed,
  onChange,
}: {
  col: TableColumn
  value: string
  original: Row[string]
  isNew: boolean
  changed: boolean
  onChange: (v: string) => void
}) {
  const id = `f-${col.name}`
  const link = refLink(col, value)
  const label = (
    <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
      <label htmlFor={id} className="font-mono text-sm font-semibold text-gray-800 dark:text-gray-100">
        {col.name}
        {!col.nullable && !col.primaryKey && <span className="ml-0.5 text-brand-600">*</span>}
      </label>
      <span className="text-xs text-gray-400">{col.dbType}</span>
      {col.comment && <span className="text-xs text-gray-500">{col.comment}</span>}
      {!col.editable && <Lock className="size-3 text-gray-400" aria-label="고칠 수 없음" />}
      {changed && <span className="text-xs font-semibold text-brand-600">바뀜</span>}
    </div>
  )

  let control
  if (col.secret) {
    control = <p className="text-sm text-gray-400">{original ? '●●●● 값이 있어요 (보안상 보여 주지 않아요)' : '값 없음'}</p>
  } else if (!col.editable) {
    control = isNew ? (
      <p className="text-sm text-gray-400">자동으로 채워져요</p>
    ) : (
      <div className={cx('rounded-lg bg-gray-50 px-3 py-2 text-sm break-all dark:bg-gray-800/60', col.kind === 'LONG_TEXT' && 'max-h-72 overflow-auto font-mono text-xs whitespace-pre-wrap')}>
        {value === '' ? <span className="text-gray-400">-</span> : col.kind === 'DATETIME' ? value.replace('T', ' ') : value}
        {link && (
          <Link to={link} className="ml-2 text-xs font-medium text-brand-700 hover:underline dark:text-brand-300">
            {col.references!.table} 에서 보기
          </Link>
        )}
      </div>
    )
  } else if (col.kind === 'YN' || col.kind === 'ENUM') {
    control = (
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)} className={inputClass}>
        {(col.nullable || value === '') && <option value="">(비움)</option>}
        {col.options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </select>
    )
  } else if (col.kind === 'DATETIME') {
    control = <input id={id} type="datetime-local" step={1} value={value} onChange={(e) => onChange(e.target.value)} className={inputClass} />
  } else if (col.kind === 'NUMBER') {
    control = <input id={id} inputMode="decimal" value={value} onChange={(e) => onChange(e.target.value)} className={cx(inputClass, 'tabular-nums')} />
  } else if ((col.maxLength ?? 0) > 500) {
    control = <textarea id={id} rows={3} maxLength={col.maxLength ?? undefined} value={value} onChange={(e) => onChange(e.target.value)} className={cx(inputClass, 'h-auto py-2')} />
  } else {
    control = <input id={id} maxLength={col.maxLength ?? undefined} value={value} onChange={(e) => onChange(e.target.value)} className={inputClass} />
  }

  return (
    <div className="flex flex-col gap-1.5">
      {label}
      {control}
      {col.editable && link && (
        <Link to={link} className="text-xs font-medium text-brand-700 hover:underline dark:text-brand-300">
          {col.references!.table} 에서 이 값 보기
        </Link>
      )}
    </div>
  )
}
