import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Database, TriangleAlert } from 'lucide-react'
import { Link } from 'react-router'
import { adminApi } from '@/lib/api'
import { CATEGORY, formatDateTime } from '@/lib/labels'
import { tableApi, tableKeys } from '@/lib/table-api'
import { Badge, Card, Spinner } from '@/ui'

const HEADLINE = [
  { table: 'users', label: '회원' },
  { table: 'teams', label: '팀' },
  { table: 'meetings', label: '회의' },
  { table: 'chat_messages', label: '채팅 메시지' },
]

const number = new Intl.NumberFormat('ko-KR')

export function DashboardPage() {
  const tables = useQuery({ queryKey: tableKeys.list, queryFn: tableApi.list })
  const errors = useQuery({ queryKey: ['admin', 'error-logs', true], queryFn: () => adminApi.errorLogs(true) })
  const count = (name: string) => tables.data?.find((t) => t.name.endsWith(name))?.rowCount

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">대시보드</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">서비스 현황을 한눈에 봐요. 숫자는 탈퇴·삭제된 행까지 포함한 전체 행 수예요.</p>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        {HEADLINE.map((h) => (
          <Link key={h.table} to={`/data/${tables.data?.find((t) => t.name.endsWith(h.table))?.name ?? h.table}`}>
            <Card className="p-4 transition-colors hover:border-brand-300 dark:hover:border-brand-700">
              <p className="text-sm text-gray-500 dark:text-gray-400">{h.label}</p>
              <p className="mt-1 text-2xl font-bold tabular-nums">{tables.isPending ? <Spinner className="size-5" /> : number.format(count(h.table) ?? 0)}</p>
            </Card>
          </Link>
        ))}
        <Link to="/errors" className="col-span-2 lg:col-span-1">
          <Card className="p-4 transition-colors hover:border-brand-300 dark:hover:border-brand-700">
            <p className="text-sm text-gray-500 dark:text-gray-400">미해결 에러</p>
            <p className="mt-1 text-2xl font-bold text-red-600 tabular-nums dark:text-red-400">
              {errors.isPending ? <Spinner className="size-5" /> : (errors.data?.length ?? 0) >= 100 ? '100+' : number.format(errors.data?.length ?? 0)}
            </p>
          </Card>
        </Link>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <div className="flex items-center justify-between border-b border-gray-100 px-5 py-3.5 dark:border-gray-800">
            <h2 className="flex items-center gap-2 font-semibold">
              <TriangleAlert className="size-4 text-red-500" />
              최근 미해결 에러
            </h2>
            <Link to="/errors" className="flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline dark:text-brand-300">
              전체 보기 <ArrowRight className="size-3.5" />
            </Link>
          </div>
          {errors.isPending ? (
            <div className="flex justify-center p-8">
              <Spinner />
            </div>
          ) : errors.data?.length ? (
            <ul className="divide-y divide-gray-100 dark:divide-gray-800">
              {errors.data.slice(0, 6).map((e) => (
                <li key={e.errorCode}>
                  <Link to={`/errors/${e.errorCode}`} className="flex items-center gap-3 px-5 py-3 hover:bg-gray-50 dark:hover:bg-gray-800/50">
                    <Badge className={CATEGORY[e.category].tone}>{CATEGORY[e.category].label}</Badge>
                    <span className="min-w-0 flex-1 truncate text-sm">{e.message ?? e.exceptionClass ?? '-'}</span>
                    <span className="shrink-0 text-xs text-gray-400 tabular-nums">{formatDateTime(e.createdAt)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <p className="p-8 text-center text-sm text-gray-500">미해결 에러가 없어요.</p>
          )}
        </Card>

        <Card>
          <div className="flex items-center justify-between border-b border-gray-100 px-5 py-3.5 dark:border-gray-800">
            <h2 className="flex items-center gap-2 font-semibold">
              <Database className="size-4 text-brand-600" />
              데이터
            </h2>
            <Link to="/data" className="flex items-center gap-1 text-sm font-medium text-brand-700 hover:underline dark:text-brand-300">
              데이터 관리 <ArrowRight className="size-3.5" />
            </Link>
          </div>
          <p className="px-5 py-4 text-sm leading-relaxed text-gray-600 dark:text-gray-300">
            모든 테이블을 같은 화면에서 찾고 고칠 수 있어요. 테이블이 새로 생기면 자동으로 목록에 나타나요. 고친 내용은
            <Link to={`/data/${tables.data?.find((t) => t.name.endsWith('admin_data_changes'))?.name ?? 'admin_data_changes'}`} className="mx-1 font-medium text-brand-700 hover:underline dark:text-brand-300">
              데이터 변경 기록
            </Link>
            에 남아요.
          </p>
        </Card>
      </div>
    </div>
  )
}
