import { useQuery } from '@tanstack/react-query'
import { Lock, RefreshCw, Search } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import { tableApi, tableKeys, type TableSummary } from '@/lib/table-api'
import { Badge, Button, Card, inputClass, Spinner } from '@/ui'

const number = new Intl.NumberFormat('ko-KR')

/** 데이터 관리: DB 의 모든 테이블 (묶음별). 새로 만든 테이블은 "기타" 묶음에 자동으로 나타난다 */
export function TablesPage() {
  const tables = useQuery({ queryKey: tableKeys.list, queryFn: tableApi.list })
  const [search, setSearch] = useState('')

  const groups = useMemo(() => {
    const term = search.trim().toLowerCase()
    const list = (tables.data ?? []).filter((t) => !term || t.name.includes(term) || t.label.toLowerCase().includes(term))
    return list.reduce<{ group: string; tables: TableSummary[] }[]>((acc, t) => {
      const g = acc.find((x) => x.group === t.group)
      if (g) g.tables.push(t)
      else acc.push({ group: t.group, tables: [t] })
      return acc
    }, [])
  }, [tables.data, search])

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">데이터 관리</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            테이블을 골라 행을 찾고, 고치고, CSV 로 내려받아요. 행은 지우지 않고 deleted_yn 으로 숨겨요.
          </p>
        </div>
        <Button variant="outline" icon={<RefreshCw className="size-4" />} onClick={() => void tables.refetch()} loading={tables.isFetching}>
          새로고침
        </Button>
      </div>

      <div className="relative max-w-sm">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-gray-400" />
        <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="테이블 이름으로 찾기" className={`${inputClass} pl-9`} />
      </div>

      {tables.isPending ? (
        <div className="flex justify-center p-12">
          <Spinner />
        </div>
      ) : tables.isError ? (
        <p className="rounded-xl bg-red-50 p-4 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300">{tables.error.message}</p>
      ) : (
        groups.map((g) => (
          <section key={g.group} className="flex flex-col gap-2">
            <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400">{g.group}</h2>
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {g.tables.map((t) => (
                <Link key={t.name} to={`/data/${t.name}`}>
                  <Card className="flex h-full flex-col gap-1.5 p-4 transition-colors hover:border-brand-300 dark:hover:border-brand-700">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold">{t.label}</span>
                      {t.readOnly && (
                        <Badge className="gap-1 bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                          <Lock className="size-3" />
                          보기 전용
                        </Badge>
                      )}
                      <span className="ml-auto text-sm font-semibold text-gray-700 tabular-nums dark:text-gray-200">{number.format(t.rowCount)}</span>
                    </div>
                    <span className="font-mono text-xs text-gray-400">{t.name}</span>
                    {t.description && <span className="text-xs text-gray-500 dark:text-gray-400">{t.description}</span>}
                  </Card>
                </Link>
              ))}
            </div>
          </section>
        ))
      )}
    </div>
  )
}
