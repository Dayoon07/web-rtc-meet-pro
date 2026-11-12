import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, RefreshCw, Search, X } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { toast } from 'sonner'
import { adminApi, type PendingSignup } from '@/lib/api'
import { formatDateTime } from '@/lib/labels'
import { Badge, Button, Card, cx, inputClass, Spinner } from '@/ui'

const KEY = ['admin', 'signups', 'pending'] as const

/** 한 줄의 승인·거절 버튼 */
function Actions({ user }: { user: PendingSignup }) {
  const queryClient = useQueryClient()
  const done = (message: string) => {
    toast.success(message)
    void queryClient.invalidateQueries({ queryKey: KEY })
  }
  const approve = useMutation({
    mutationFn: () => adminApi.verifyEmail(user.id),
    onSuccess: () => done(`${user.email} 이메일 인증을 승인했어요.`),
    onError: (e: Error) => toast.error(e.message),
  })
  const reject = useMutation({
    mutationFn: () => adminApi.rejectSignup(user.id),
    onSuccess: () => done(`${user.email} 가입을 거절했어요. 같은 이메일로 다시 가입할 수 있어요.`),
    onError: (e: Error) => toast.error(e.message),
  })
  const busy = approve.isPending || reject.isPending
  return (
    <div className="flex justify-end gap-2">
      <Button
        icon={<Check className="size-4" />}
        loading={approve.isPending}
        disabled={busy}
        onClick={() => {
          if (window.confirm(`${user.email}\n이메일 인증을 승인할까요? 인증 메일 없이 바로 서비스를 쓸 수 있게 돼요.`)) approve.mutate()
        }}
      >
        승인
      </Button>
      <Button
        variant="outline"
        icon={<X className="size-4" />}
        loading={reject.isPending}
        disabled={busy}
        className="text-red-600 dark:text-red-400"
        onClick={() => {
          if (window.confirm(`${user.email}\n가입을 거절할까요? 계정의 개인정보를 지우고 닫아요. 되돌릴 수 없어요.`)) reject.mutate()
        }}
      >
        거절
      </Button>
    </div>
  )
}

/**
 * 이메일 인증: 인증 메일을 못 받았거나(스팸·회사 메일 차단) 잘못된 메일로 가입한 사람을 처리한다.
 * 승인 = 인증된 것으로, 거절 = 가입 취소 (개인정보를 지우고 계정을 닫는다 → 같은 이메일로 다시 가입 가능).
 * 처리한 내용은 데이터 변경 기록에 남는다.
 */
export function PendingSignupsPage() {
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const list = useQuery({ queryKey: [...KEY, q], queryFn: () => adminApi.pendingSignups(q) })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setQ(search.trim())
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">이메일 인증</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            가입하고 아직 이메일 인증을 안 한 계정이에요. 승인하면 바로 서비스를 쓸 수 있고, 거절하면 가입이 취소돼요 (같은 이메일로 다시 가입 가능).
          </p>
        </div>
        <Button variant="outline" icon={<RefreshCw className="size-4" />} onClick={() => void list.refetch()} loading={list.isFetching}>
          새로고침
        </Button>
      </div>

      <Card className="p-3">
        <form onSubmit={submit} className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-gray-400" />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="이메일 · 닉네임 · 이름으로 찾기 — Enter" className={cx(inputClass, 'pl-9')} />
        </form>
      </Card>

      <Card className="overflow-hidden">
        {list.isError ? (
          <p className="p-4 text-sm text-red-700 dark:text-red-300">{list.error.message}</p>
        ) : !list.data ? (
          <div className="flex justify-center p-12">
            <Spinner />
          </div>
        ) : list.data.length === 0 ? (
          <p className="p-12 text-center text-sm text-gray-500">인증을 기다리는 계정이 없어요.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[960px] text-left text-sm whitespace-nowrap">
              <thead className="border-b border-gray-200 bg-gray-50 text-xs text-gray-500 dark:border-gray-800 dark:bg-gray-800/40 dark:text-gray-400">
                <tr>
                  <th className="px-4 py-2.5 font-medium">ID</th>
                  <th className="px-4 py-2.5 font-medium">이메일</th>
                  <th className="px-4 py-2.5 font-medium">닉네임 · 이름</th>
                  <th className="px-4 py-2.5 font-medium">가입 방식</th>
                  <th className="px-4 py-2.5 font-medium">가입</th>
                  <th className="px-4 py-2.5 font-medium">마지막 로그인</th>
                  <th className="px-4 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                {list.data.map((u) => (
                  <tr key={u.id} className="align-middle">
                    <td className="px-4 py-3 font-mono text-xs text-gray-500">{u.id}</td>
                    <td className="px-4 py-3 font-medium">{u.email}</td>
                    <td className="px-4 py-3">
                      {u.nickname}
                      {u.realName && <span className="text-gray-500"> · {u.realName}</span>}
                    </td>
                    <td className="px-4 py-3">
                      <Badge className="bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300">{u.hasPassword ? '이메일' : '소셜'}</Badge>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-gray-600 dark:text-gray-400">{formatDateTime(u.createdAt)}</td>
                    <td className="px-4 py-3 whitespace-nowrap text-gray-600 dark:text-gray-400">{u.lastLoginAt ? formatDateTime(u.lastLoginAt) : '-'}</td>
                    <td className="px-4 py-3">
                      <Actions user={u} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
