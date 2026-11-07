import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ArrowDown, ArrowLeft, ArrowUp, ChevronLeft, ChevronRight, Download, History, Lock, Plus, RefreshCw, Search, X } from 'lucide-react'
import { type FormEvent, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { type RowQuery, tableApi, tableKeys } from '@/lib/table-api'
import { Badge, Button, Card, cx, inputClass, Spinner } from '@/ui'
import { Cell } from './cells'
import { RowPanel } from './RowPanel'

const number = new Intl.NumberFormat('ko-KR')
const SIZES = [25, 50, 100, 200]

/** 주소(?q=&sort=&desc=&page=&size=&deleted=&filter=col:value&row=)에서 조건을 읽는다 → 링크로 공유하고 새로고침해도 그대로 */
function readQuery(params: URLSearchParams): RowQuery {
  const deleted = params.get('deleted')
  return {
    q: params.get('q') ?? undefined,
    sort: params.get('sort') ?? undefined,
    desc: params.get('desc') !== 'false',
    page: Number(params.get('page') ?? 0) || 0,
    size: Number(params.get('size') ?? 50) || 50,
    deleted: deleted === 'active' || deleted === 'deleted' ? deleted : undefined,
    filters: params.getAll('filter').filter((f) => f.includes(':')).map((f) => [f.slice(0, f.indexOf(':')), f.slice(f.indexOf(':') + 1)] as [string, string]),
  }
}

/** 한 테이블의 행 목록: 검색·정렬·필터·페이지, 행을 누르면 오른쪽 창에서 보고 고친다 */
export function TableDataPage() {
  const { table = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const query = readQuery(params)
  const openRow = params.get('row')
  const [search, setSearch] = useState(query.q ?? '')
  useEffect(() => setSearch(params.get('q') ?? ''), [params])

  const tables = useQuery({ queryKey: tableKeys.list, queryFn: tableApi.list })
  const meta = useQuery({ queryKey: tableKeys.meta(table), queryFn: () => tableApi.meta(table) })
  const rows = useQuery({
    queryKey: [...tableKeys.rows(table), params.toString()],
    queryFn: () => tableApi.rows(table, query),
    placeholderData: keepPreviousData,
  })
  const historyTable = tables.data?.find((t) => t.name.endsWith('admin_data_changes'))?.name ?? null

  /** 조건을 바꾸면 첫 페이지로 (페이지 이동만 예외) */
  const update = (change: (p: URLSearchParams) => void, keepPage = false) => {
    const next = new URLSearchParams(params)
    change(next)
    if (!keepPage) next.delete('page')
    setParams(next)
  }
  const setRow = (id: string | null) => update((p) => (id ? p.set('row', id) : p.delete('row')), true)

  const sortBy = (col: string) =>
    update((p) => {
      if (query.sort === col) p.set('desc', query.desc ? 'false' : 'true')
      else {
        p.set('sort', col)
        p.delete('desc')
      }
    })
  const submitSearch = (e: FormEvent) => {
    e.preventDefault()
    update((p) => (search.trim() ? p.set('q', search.trim()) : p.delete('q')))
  }
  const removeFilter = (index: number) =>
    update((p) => {
      const rest = p.getAll('filter').filter((_, i) => i !== index)
      p.delete('filter')
      rest.forEach((f) => p.append('filter', f))
    })
  const exportCsv = () => tableApi.exportCsv(table, query).catch((e: Error) => toast.error(e.message))

  if (meta.isPending) {
    return (
      <div className="flex justify-center p-12">
        <Spinner />
      </div>
    )
  }
  if (meta.isError) return <p className="rounded-xl bg-red-50 p-4 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300">{meta.error.message}</p>

  const m = meta.data
  const page = rows.data
  const lastPage = page ? Math.max(0, Math.ceil(page.total / page.size) - 1) : 0

  return (
    <div className="flex flex-col gap-4">
      <Link to="/data" className="inline-flex items-center gap-1.5 self-start text-sm font-medium text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white">
        <ArrowLeft className="size-4" />
        데이터 관리
      </Link>

      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-0 flex-1 basis-64">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight">{m.label}</h1>
            <span className="font-mono text-sm text-gray-400">{m.name}</span>
            {m.readOnly && (
              <Badge className="gap-1 bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                <Lock className="size-3" />
                보기 전용
              </Badge>
            )}
          </div>
          {m.description && <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{m.description}</p>}
        </div>
        <div className="flex flex-wrap gap-2">
          {historyTable && historyTable !== m.name && (
            <Link
              to={`/data/${historyTable}?filter=table_name:${m.name}`}
              className="inline-flex h-9 items-center gap-2 rounded-lg px-3.5 text-sm font-semibold text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
            >
              <History className="size-4" />
              변경 기록
            </Link>
          )}
          <Button variant="outline" icon={<RefreshCw className="size-4" />} onClick={() => void rows.refetch()} loading={rows.isFetching}>
            새로고침
          </Button>
          <Button variant="outline" icon={<Download className="size-4" />} onClick={() => void exportCsv()}>
            CSV
          </Button>
          {m.insertable && (
            <Button icon={<Plus className="size-4" />} onClick={() => setRow('new')}>
              행 추가
            </Button>
          )}
        </div>
      </div>

      <Card className="flex flex-col gap-3 p-3">
        <div className="flex flex-col gap-2 lg:flex-row lg:items-center">
          <form onSubmit={submitSearch} className="relative flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-gray-400" />
            <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="글자 컬럼 전체에서 찾기 (숫자는 ID 도 찾아요) — Enter" className={`${inputClass} pl-9`} />
          </form>
          {m.softDelete && (
            <div className="flex rounded-lg bg-gray-100 p-1 text-sm dark:bg-gray-800">
              {([
                [undefined, '전체'],
                ['active', '사용 중 (N)'],
                ['deleted', '미사용 (Y)'],
              ] as const).map(([value, label]) => (
                <button
                  key={label}
                  type="button"
                  onClick={() => update((p) => (value ? p.set('deleted', value) : p.delete('deleted')))}
                  className={cx(
                    'h-8 rounded-md px-3 font-medium whitespace-nowrap',
                    query.deleted === value ? 'bg-white text-gray-900 shadow-xs dark:bg-gray-700 dark:text-white' : 'text-gray-500 hover:text-gray-800 dark:hover:text-gray-200',
                  )}
                >
                  {label}
                </button>
              ))}
            </div>
          )}
        </div>
        {(query.filters?.length || query.q) && (
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="text-gray-500">조건</span>
            {query.q && (
              <button type="button" onClick={() => update((p) => p.delete('q'))} className="inline-flex h-7 items-center gap-1 rounded-full bg-brand-50 px-2.5 text-brand-700 dark:bg-brand-500/15 dark:text-brand-200">
                “{query.q}” <X className="size-3.5" />
              </button>
            )}
            {query.filters?.map(([c, v], i) => (
              <button
                key={`${c}-${i}`}
                type="button"
                onClick={() => removeFilter(i)}
                className="inline-flex h-7 items-center gap-1 rounded-full bg-brand-50 px-2.5 font-mono text-xs text-brand-700 dark:bg-brand-500/15 dark:text-brand-200"
              >
                {c} = {v === '' ? 'NULL' : v} <X className="size-3.5" />
              </button>
            ))}
          </div>
        )}
      </Card>

      <Card className="overflow-hidden">
        {rows.isError ? (
          <p className="p-4 text-sm text-red-700 dark:text-red-300">{rows.error.message}</p>
        ) : !page ? (
          <div className="flex justify-center p-12">
            <Spinner />
          </div>
        ) : page.rows.length === 0 ? (
          <p className="p-12 text-center text-sm text-gray-500">조건에 맞는 행이 없어요.</p>
        ) : (
          <div className={cx('overflow-x-auto', rows.isFetching && 'opacity-60')}>
            <table className="w-full text-left text-sm">
              <thead className="border-b border-gray-200 bg-gray-50 text-xs text-gray-500 dark:border-gray-800 dark:bg-gray-800/40 dark:text-gray-400">
                <tr>
                  {m.columns.map((c) => {
                    const sortable = !c.secret && c.kind !== 'LONG_TEXT' && c.kind !== 'BINARY'
                    const active = (query.sort ?? m.primaryKey) === c.name
                    return (
                      <th key={c.name} className="px-3 py-2.5 font-medium whitespace-nowrap">
                        {sortable ? (
                          <button type="button" onClick={() => sortBy(c.name)} className={cx('inline-flex items-center gap-1 font-mono hover:text-gray-900 dark:hover:text-white', active && 'text-brand-700 dark:text-brand-300')} title={c.comment ?? c.dbType}>
                            {c.name}
                            {active && (query.desc ? <ArrowDown className="size-3" /> : <ArrowUp className="size-3" />)}
                          </button>
                        ) : (
                          <span className="font-mono" title={c.comment ?? c.dbType}>
                            {c.name}
                          </span>
                        )}
                      </th>
                    )
                  })}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                {page.rows.map((r, i) => {
                  const id = m.primaryKey ? String(r[m.primaryKey]) : null
                  return (
                    <tr
                      key={id ?? i}
                      onClick={() => id && setRow(id)}
                      className={cx(id && 'cursor-pointer hover:bg-brand-50/50 dark:hover:bg-brand-500/5', r.deleted_yn === 'Y' && 'text-gray-400 dark:text-gray-500', openRow === id && 'bg-brand-50 dark:bg-brand-500/10')}
                    >
                      {m.columns.map((c) => (
                        <td key={c.name} className="px-3 py-2 align-top">
                          <Cell col={c} value={r[c.name] ?? null} />
                        </td>
                      ))}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        {page && page.total > 0 && (
          <div className="flex flex-wrap items-center gap-3 border-t border-gray-100 px-4 py-2.5 text-sm dark:border-gray-800">
            <span className="text-gray-500">
              전체 <b className="text-gray-800 tabular-nums dark:text-gray-100">{number.format(page.total)}</b>행 · {number.format(page.page * page.size + 1)}–
              {number.format(Math.min(page.total, (page.page + 1) * page.size))}
            </span>
            <select
              aria-label="한 페이지 행 수"
              value={page.size}
              onChange={(e) => update((p) => p.set('size', e.target.value))}
              className="h-8 rounded-lg border border-gray-300 bg-white px-2 text-sm dark:border-gray-700 dark:bg-gray-900"
            >
              {SIZES.map((s) => (
                <option key={s} value={s}>
                  {s}행씩
                </option>
              ))}
            </select>
            <div className="ml-auto flex items-center gap-1">
              <Button variant="ghost" aria-label="이전 페이지" disabled={page.page <= 0} onClick={() => update((p) => p.set('page', String(page.page - 1)), true)} icon={<ChevronLeft className="size-4" />} />
              <span className="tabular-nums">
                {page.page + 1} / {lastPage + 1}
              </span>
              <Button variant="ghost" aria-label="다음 페이지" disabled={page.page >= lastPage} onClick={() => update((p) => p.set('page', String(page.page + 1)), true)} icon={<ChevronRight className="size-4" />} />
            </div>
          </div>
        )}
      </Card>

      {openRow && (
        <RowPanel
          key={`${m.name}-${openRow}`}
          meta={m}
          rowId={openRow}
          historyTable={historyTable}
          onClose={() => setRow(null)}
          onSaved={(saved) => m.primaryKey && setRow(String(saved[m.primaryKey]))}
        />
      )}
    </div>
  )
}
