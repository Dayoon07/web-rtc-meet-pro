import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw, Search, Undo2, X } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { toast } from 'sonner'
import { type AdminPayment, adminApi } from '@/lib/api'
import { formatDateTime } from '@/lib/labels'
import { Badge, Button, Card, cx, inputClass, Spinner } from '@/ui'

const KEY = ['admin', 'payments'] as const
const won = (n: number) => `${n.toLocaleString('ko-KR')}원`

const STATUS: Record<string, { label: string; className: string }> = {
  DONE: { label: '결제 완료', className: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-500/15 dark:text-emerald-300' },
  CANCELED: { label: '환불됨', className: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300' },
  FAILED: { label: '실패', className: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300' },
  READY: { label: '처리 중', className: 'bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-300' },
  ABORTED: { label: '중단', className: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300' },
}

const RULE: Record<string, string> = {
  FULL_7_DAYS: '결제 7일 안 → 전액',
  PRORATED: '쓴 날수만큼 빼고',
  NONE: '약관상 환불 불가',
}

function RefundDialog({ payment, onClose }: { payment: AdminPayment; onClose: () => void }) {
  const queryClient = useQueryClient()
  const quote = useQuery({ queryKey: [...KEY, payment.orderId, 'quote'], queryFn: () => adminApi.refundQuote(payment.orderId), staleTime: 0 })
  const [amount, setAmount] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const q = quote.data
  const value = amount ?? String(q?.refundAmount ?? '')
  const parsed = Number(value)
  const valid = Number.isInteger(parsed) && parsed > 0 && parsed <= payment.amount

  const refund = useMutation({
    mutationFn: () => adminApi.refund(payment.orderId, parsed, reason.trim()),
    onSuccess: () => {
      toast.success(`${payment.orderId} — ${won(parsed)} 환불했어요. 그 플랜은 바로 끝났어요.`)
      void queryClient.invalidateQueries({ queryKey: KEY })
      onClose()
    },
    onError: (e: Error) => toast.error(e.message),
  })
  const blockedHard = q?.blockedBy === 'REFUND_ALREADY_DONE' || q?.blockedBy === 'REFUND_NO_PAYMENT_KEY' || q?.blockedBy === 'REFUND_NOT_PAID'

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-gray-950/40 p-4" role="dialog" aria-modal aria-label="결제 환불">
      <Card className="flex w-full max-w-md flex-col gap-4 p-5">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold">결제 환불</h2>
          <button type="button" onClick={onClose} aria-label="닫기" className="rounded-md p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800">
            <X className="size-5" />
          </button>
        </div>
        {quote.isPending ? (
          <div className="flex justify-center py-6">
            <Spinner />
          </div>
        ) : quote.isError || !q ? (
          <p className="text-sm text-red-600">{quote.error?.message}</p>
        ) : (
          <>
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 rounded-xl bg-gray-50 p-4 text-sm dark:bg-gray-800/60">
              <dt className="text-gray-500">주문</dt>
              <dd className="text-right">{q.orderName}</dd>
              <dt className="text-gray-500">회원</dt>
              <dd className="text-right">{payment.userEmail ?? payment.userId}</dd>
              <dt className="text-gray-500">결제 금액</dt>
              <dd className="text-right tabular-nums">{won(q.paidAmount)}</dd>
              <dt className="text-gray-500">약관 계산</dt>
              <dd className="text-right">
                {RULE[q.rule]}
                {q.rule === 'PRORATED' && q.periodDays > 0 && ` (${q.usedDays}/${q.periodDays}일 사용)`} · <b className="tabular-nums">{won(q.refundAmount)}</b>
              </dd>
            </dl>
            {blockedHard ? (
              <p className="rounded-lg bg-gray-50 p-3 text-sm text-gray-600 dark:bg-gray-800/60 dark:text-gray-300">
                {q.blockedBy === 'REFUND_ALREADY_DONE' ? '이미 환불한 결제예요.' : q.blockedBy === 'REFUND_NO_PAYMENT_KEY' ? '토스 결제 정보가 없는 결제(테스트 데이터)라 환불할 수 없어요.' : '승인되지 않은 결제예요.'}
              </p>
            ) : (
              <>
                {q.cancelsPlan && <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">환불하면 {q.planName} 플랜이 바로 취소돼요.</p>}
                <label className="flex flex-col gap-1 text-sm font-medium">
                  환불 금액 (원, 결제 금액까지)
                  <input inputMode="numeric" value={value} onChange={(e) => setAmount(e.target.value.replace(/[^0-9]/g, ''))} className={cx(inputClass, 'tabular-nums')} />
                </label>
                <label className="flex flex-col gap-1 text-sm font-medium">
                  사유
                  <input value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} placeholder="예: 고객 요청, 장애 보상" className={inputClass} />
                </label>
                <div className="flex justify-end gap-2">
                  <Button variant="ghost" onClick={onClose}>
                    취소
                  </Button>
                  <Button
                    icon={<Undo2 className="size-4" />}
                    loading={refund.isPending}
                    disabled={!valid || !reason.trim()}
                    className="bg-red-600 hover:bg-red-700 dark:bg-red-500 dark:hover:bg-red-400"
                    onClick={() => {
                      if (window.confirm(`${won(parsed)} 환불할까요? 토스로 바로 결제가 취소되고 되돌릴 수 없어요.`)) refund.mutate()
                    }}
                  >
                    {valid ? `${won(parsed)} 환불` : '환불'}
                  </Button>
                </div>
              </>
            )}
          </>
        )}
      </Card>
    </div>
  )
}

export function PaymentsPage() {
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('')
  const [refunding, setRefunding] = useState<AdminPayment | null>(null)
  const list = useQuery({ queryKey: [...KEY, q, status], queryFn: () => adminApi.payments(q, status) })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    setQ(search.trim())
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">결제·환불</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            약관 제8조대로 계산한 금액을 보여 줘요 (7일 안 전액, 그 뒤에는 쓴 날수만큼 빼고). 금액을 바꿔서 환불할 수도 있어요. 환불하면 그 플랜은 바로 끝나요.
          </p>
        </div>
        <Button variant="outline" icon={<RefreshCw className="size-4" />} onClick={() => void list.refetch()} loading={list.isFetching}>
          새로고침
        </Button>
      </div>

      <Card className="flex flex-col gap-2 p-3 sm:flex-row">
        <form onSubmit={submit} className="relative flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-gray-400" />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="주문번호 · 이메일 · 주문명 · 팀 이름 — Enter" className={cx(inputClass, 'pl-9')} />
        </form>
        <select aria-label="상태" value={status} onChange={(e) => setStatus(e.target.value)} className={cx(inputClass, 'sm:w-40')}>
          <option value="">전체 상태</option>
          {Object.entries(STATUS).map(([key, s]) => (
            <option key={key} value={key}>
              {s.label}
            </option>
          ))}
        </select>
      </Card>

      <Card className="overflow-hidden">
        {list.isError ? (
          <p className="p-4 text-sm text-red-700 dark:text-red-300">{list.error.message}</p>
        ) : !list.data ? (
          <div className="flex justify-center p-12">
            <Spinner />
          </div>
        ) : list.data.length === 0 ? (
          <p className="p-12 text-center text-sm text-gray-500">결제가 없어요.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1000px] text-left text-sm whitespace-nowrap">
              <thead className="border-b border-gray-200 bg-gray-50 text-xs text-gray-500 dark:border-gray-800 dark:bg-gray-800/40 dark:text-gray-400">
                <tr>
                  <th className="px-4 py-2.5 font-medium">결제일</th>
                  <th className="px-4 py-2.5 font-medium">회원</th>
                  <th className="px-4 py-2.5 font-medium">주문</th>
                  <th className="px-4 py-2.5 text-right font-medium">금액</th>
                  <th className="px-4 py-2.5 font-medium">상태</th>
                  <th className="px-4 py-2.5" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                {list.data.map((p) => {
                  const s = STATUS[p.status] ?? { label: p.status, className: 'bg-gray-100 text-gray-700' }
                  return (
                    <tr key={p.orderId} className="align-middle">
                      <td className="px-4 py-3 text-gray-600 dark:text-gray-400">{formatDateTime(p.approvedAt ?? p.createdAt)}</td>
                      <td className="px-4 py-3">{p.userEmail ?? `#${p.userId}`}</td>
                      <td className="px-4 py-3">
                        <span className="block max-w-80 truncate">{p.orderName}</span>
                        <span className="font-mono text-xs text-gray-400">{p.orderId}</span>
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums">{won(p.amount)}</td>
                      <td className="px-4 py-3">
                        <Badge className={s.className}>{s.label}</Badge>
                        {p.refundedAmount > 0 && (
                          <span className="block text-xs text-gray-500" title={p.refundReason ?? undefined}>
                            환불 {won(p.refundedAmount)}
                            {p.refundedAt && ` · ${formatDateTime(p.refundedAt)}`}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {p.status === 'DONE' && (
                          <Button variant="outline" icon={<Undo2 className="size-4" />} onClick={() => setRefunding(p)}>
                            환불
                          </Button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {refunding && <RefundDialog payment={refunding} onClose={() => setRefunding(null)} />}
    </div>
  )
}
