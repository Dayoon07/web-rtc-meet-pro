import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Check, CheckCircle2, Copy } from 'lucide-react'
import { type ReactNode, useState } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { adminApi, ApiError, type ErrorLog } from '@/lib/api'
import { CATEGORY, formatDateTime, SOURCE } from '@/lib/labels'
import { Badge, Button, Card, Spinner } from '@/ui'

function CopyButton({ text, label }: { text: string; label: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={() =>
        void navigator.clipboard?.writeText(text).then(() => {
          setCopied(true)
          setTimeout(() => setCopied(false), 1500)
        })
      }
      className="rounded-md p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200"
    >
      {copied ? <Check className="size-4 text-emerald-600" /> : <Copy className="size-4" />}
    </button>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 py-2.5 sm:grid-cols-[9rem_1fr] sm:gap-4">
      <dt className="text-sm text-gray-500 dark:text-gray-400">{label}</dt>
      <dd className="min-w-0 text-sm break-words">{children}</dd>
    </div>
  )
}

export function ErrorLogDetailPage() {
  const { code = '' } = useParams()
  const queryClient = useQueryClient()
  const key = ['admin', 'error-log', code]
  const log = useQuery({ queryKey: key, queryFn: () => adminApi.errorLog(code), retry: false })

  const resolve = useMutation({
    mutationFn: () => adminApi.resolve(code),
    onSuccess: (updated) => {
      queryClient.setQueryData<ErrorLog>(key, (old) => ({ ...updated, stackTrace: old?.stackTrace ?? null }))
      void queryClient.invalidateQueries({ queryKey: ['admin', 'error-logs'] })
      toast.success('해결 표시했어요')
    },
    onError: (e) => toast.error(e.message),
  })

  const back = (
    <Link to="/errors" className="inline-flex items-center gap-1.5 text-sm font-medium text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white">
      <ArrowLeft className="size-4" />
      에러 기록
    </Link>
  )

  if (log.isPending) {
    return (
      <div className="flex justify-center py-16">
        <Spinner className="size-6" />
      </div>
    )
  }
  if (log.isError) {
    const notFound = log.error instanceof ApiError && log.error.status === 404
    return (
      <div className="flex flex-col gap-4">
        {back}
        <Card className="px-6 py-14 text-center">
          <p className="font-semibold">{notFound ? '그런 에러 코드를 찾을 수 없어요' : '불러오지 못했어요'}</p>
          <p className="mt-1 font-mono text-sm break-all text-gray-500">{notFound ? code : log.error.message}</p>
        </Card>
      </div>
    )
  }

  const l = log.data
  const category = CATEGORY[l.category]
  return (
    <div className="flex flex-col gap-4">
      {back}

      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1">
            <h1 className="font-mono text-lg font-bold break-all sm:text-xl">{l.errorCode}</h1>
            <CopyButton text={l.errorCode} label="에러 코드 복사" />
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <Badge className={category.tone}>{category.label}</Badge>
            <Badge className="bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300">{SOURCE[l.source]}</Badge>
            {l.resolved ? (
              <Badge className="bg-emerald-100 text-emerald-800 dark:bg-emerald-500/15 dark:text-emerald-300">해결</Badge>
            ) : (
              <Badge className="bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300">미해결</Badge>
            )}
            <span className="text-sm text-gray-500 dark:text-gray-400">{formatDateTime(l.createdAt)}</span>
          </div>
        </div>
        {!l.resolved && (
          <Button icon={<CheckCircle2 className="size-4" />} loading={resolve.isPending} onClick={() => resolve.mutate()}>
            해결 표시
          </Button>
        )}
      </div>

      <Card className="p-5">
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400">원인 분류</h2>
        <p className="mt-1 font-semibold">{category.label}</p>
        <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">{category.hint}</p>
      </Card>

      <Card className="px-5 py-2">
        <dl className="divide-y divide-gray-100 dark:divide-gray-800">
          <Row label="요청 · 화면">
            <span className="font-mono text-xs">{[l.httpMethod, l.requestPath].filter(Boolean).join(' ') || '-'}</span>
          </Row>
          <Row label="사용자 id">{l.userId ?? '로그인 전'}</Row>
          <Row label="IP">{l.ipAddress ?? '-'}</Row>
          <Row label="브라우저 · 기기">
            <span className="text-xs break-all text-gray-600 dark:text-gray-300">{l.userAgent ?? '-'}</span>
          </Row>
          <Row label="예외 종류">
            <span className="font-mono text-xs break-all">{l.exceptionClass ?? '-'}</span>
          </Row>
          <Row label="마지막 수정">
            {formatDateTime(l.editedAt)}
            {l.editedBy != null && <span className="text-gray-500"> · 관리자 #{l.editedBy}</span>}
          </Row>
        </dl>
      </Card>

      <Card className="p-5">
        <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400">메시지</h2>
        <pre className="mt-2 font-mono text-xs leading-relaxed break-words whitespace-pre-wrap">{l.message ?? '-'}</pre>
      </Card>

      <Card className="p-5">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-500 dark:text-gray-400">스택 트레이스</h2>
          {l.stackTrace && <CopyButton text={l.stackTrace} label="스택 트레이스 복사" />}
        </div>
        {l.stackTrace ? (
          <pre className="mt-2 max-h-[32rem] overflow-auto rounded-lg bg-gray-950 p-4 font-mono text-xs leading-relaxed text-gray-100">{l.stackTrace}</pre>
        ) : (
          <p className="mt-2 text-sm text-gray-500">없음</p>
        )}
      </Card>
    </div>
  )
}
