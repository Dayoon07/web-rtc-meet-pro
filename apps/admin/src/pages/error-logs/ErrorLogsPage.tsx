import { useQuery } from '@tanstack/react-query'
import { RefreshCw, Search } from 'lucide-react'
import { type FormEvent, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { adminApi, ERROR_CODE, type ErrorCategory, type ErrorLog } from '@/lib/api'
import { CATEGORY, formatDateTime, SOURCE } from '@/lib/labels'
import { Badge, Button, Card, cx, inputClass, Spinner } from '@/ui'

function StatusBadge({ resolved }: { resolved: boolean }) {
  return resolved ? (
    <Badge className="bg-emerald-100 text-emerald-800 dark:bg-emerald-500/15 dark:text-emerald-300">해결</Badge>
  ) : (
    <Badge className="bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300">미해결</Badge>
  )
}

const request = (log: ErrorLog) => [log.httpMethod, log.requestPath].filter(Boolean).join(' ') || '-'

export function ErrorLogsPage() {
  const navigate = useNavigate()
  const [unresolvedOnly, setUnresolvedOnly] = useState(true)
  const [category, setCategory] = useState<ErrorCategory | null>(null)
  const [code, setCode] = useState('')
  const [codeError, setCodeError] = useState<string | null>(null)

  const logs = useQuery({ queryKey: ['admin', 'error-logs', unresolvedOnly], queryFn: () => adminApi.errorLogs(unresolvedOnly) })
  const counts = useMemo(() => {
    const map = new Map<ErrorCategory, number>()
    logs.data?.forEach((l) => map.set(l.category, (map.get(l.category) ?? 0) + 1))
    return [...map.entries()].sort((a, b) => b[1] - a[1])
  }, [logs.data])
  const shown = (logs.data ?? []).filter((l) => !category || l.category === category)

  const find = (e: FormEvent) => {
    e.preventDefault()
    const value = code.trim().toUpperCase()
    if (!ERROR_CODE.test(value)) {
      setCodeError('ERR-날짜8자리-영문·숫자20자리 형식이에요. 예: ERR-20261107-3F2A9C4B1D7E4F60A8B2')
      return
    }
    navigate(`/errors/${value}`)
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">에러 기록</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">사용자가 알려 준 에러 코드로 찾거나, 최근 에러를 확인해요.</p>
      </div>

      <Card className="p-4">
        <form onSubmit={find} className="flex flex-col gap-2 sm:flex-row">
          <input
            value={code}
            onChange={(e) => {
              setCode(e.target.value)
              setCodeError(null)
            }}
            placeholder="에러 코드 (ERR-...)"
            aria-label="에러 코드"
            className={cx(inputClass, 'font-mono')}
          />
          <Button type="submit" icon={<Search className="size-4" />} className="h-10">
            찾기
          </Button>
        </form>
        {codeError && <p className="mt-2 text-sm text-red-600 dark:text-red-400">{codeError}</p>}
      </Card>

      <div className="flex flex-wrap items-center gap-2">
        <div className="inline-flex rounded-lg border border-gray-300 bg-white p-0.5 dark:border-gray-700 dark:bg-gray-900" role="group" aria-label="해결 여부">
          {[
            { value: true, label: '미해결만' },
            { value: false, label: '전체' },
          ].map((o) => (
            <button
              key={o.label}
              type="button"
              onClick={() => {
                setUnresolvedOnly(o.value)
                setCategory(null)
              }}
              aria-pressed={unresolvedOnly === o.value}
              className={cx(
                'h-8 rounded-md px-3 text-sm font-medium',
                unresolvedOnly === o.value ? 'bg-brand-600 text-white dark:bg-brand-500' : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800',
              )}
            >
              {o.label}
            </button>
          ))}
        </div>
        <Button variant="outline" icon={<RefreshCw className="size-4" />} loading={logs.isFetching} onClick={() => void logs.refetch()}>
          새로고침
        </Button>
        <span className="ml-auto text-sm text-gray-500 dark:text-gray-400">최근 100건까지</span>
      </div>

      {counts.length > 0 && (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setCategory(null)}
            className={cx('rounded-full px-3 py-1 text-sm font-medium ring-1', !category ? 'bg-gray-900 text-white ring-gray-900 dark:bg-white dark:text-gray-900 dark:ring-white' : 'ring-gray-300 dark:ring-gray-700')}
          >
            전체 {logs.data?.length}
          </button>
          {counts.map(([c, n]) => (
            <button
              key={c}
              type="button"
              onClick={() => setCategory(category === c ? null : c)}
              title={CATEGORY[c].hint}
              className={cx('rounded-full px-3 py-1 text-sm font-medium', CATEGORY[c].tone, category === c && 'ring-2 ring-brand-500')}
            >
              {CATEGORY[c].label} {n}
            </button>
          ))}
        </div>
      )}

      {logs.isPending ? (
        <div className="flex justify-center py-16">
          <Spinner className="size-6" />
        </div>
      ) : logs.isError ? (
        <p className="rounded-xl bg-red-50 p-4 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300">{logs.error.message}</p>
      ) : shown.length === 0 ? (
        <Card className="px-6 py-16 text-center">
          <p className="font-semibold">{unresolvedOnly ? '해결 안 된 에러가 없어요' : '에러 기록이 없어요'}</p>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">서버 500 오류나 화면 오류가 나면 여기에 쌓여요.</p>
        </Card>
      ) : (
        <Card className="overflow-hidden">
          <table className="hidden w-full text-left text-sm md:table">
            <thead className="border-b border-gray-200 bg-gray-50 text-xs text-gray-500 dark:border-gray-800 dark:bg-gray-900/60 dark:text-gray-400">
              <tr>
                <th className="px-4 py-3 font-medium">발생 시각</th>
                <th className="px-4 py-3 font-medium">에러 코드</th>
                <th className="px-4 py-3 font-medium">출처</th>
                <th className="px-4 py-3 font-medium">원인</th>
                <th className="px-4 py-3 font-medium">요청 · 화면</th>
                <th className="px-4 py-3 font-medium">사용자</th>
                <th className="px-4 py-3 font-medium">상태</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {shown.map((l) => (
                <tr key={l.errorCode} onClick={() => navigate(`/errors/${l.errorCode}`)} className="cursor-pointer hover:bg-brand-50/50 dark:hover:bg-brand-500/5">
                  <td className="px-4 py-3 whitespace-nowrap text-gray-600 dark:text-gray-400">{formatDateTime(l.createdAt)}</td>
                  <td className="px-4 py-3">
                    <Link to={`/errors/${l.errorCode}`} className="font-mono text-xs font-medium text-brand-700 hover:underline dark:text-brand-300">
                      {l.errorCode}
                    </Link>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap">{SOURCE[l.source]}</td>
                  <td className="px-4 py-3">
                    <Badge className={CATEGORY[l.category].tone}>{CATEGORY[l.category].label}</Badge>
                  </td>
                  <td className="max-w-72 truncate px-4 py-3 font-mono text-xs text-gray-600 dark:text-gray-400" title={request(l)}>
                    {request(l)}
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-gray-600 dark:text-gray-400">{l.userId ?? '-'}</td>
                  <td className="px-4 py-3">
                    <StatusBadge resolved={l.resolved} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <ul className="divide-y divide-gray-100 md:hidden dark:divide-gray-800">
            {shown.map((l) => (
              <li key={l.errorCode}>
                <Link to={`/errors/${l.errorCode}`} className="flex flex-col gap-1.5 px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-800/50">
                  <span className="flex items-center gap-2">
                    <Badge className={CATEGORY[l.category].tone}>{CATEGORY[l.category].label}</Badge>
                    <StatusBadge resolved={l.resolved} />
                    <span className="ml-auto text-xs text-gray-500">{formatDateTime(l.createdAt)}</span>
                  </span>
                  <span className="font-mono text-xs font-medium break-all text-brand-700 dark:text-brand-300">{l.errorCode}</span>
                  <span className="truncate font-mono text-xs text-gray-500">
                    {SOURCE[l.source]} · {request(l)}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  )
}
