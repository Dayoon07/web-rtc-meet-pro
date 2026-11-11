import { ExternalLink, Receipt } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { SUPPORT_EMAIL } from '@/shared/config/env'
import { cn } from '@/shared/lib/cn'
import { useFormat } from '@/shared/lib/format'
import { Button, Modal } from '@/shared/ui'
import type { Payment } from '../model/types'
import { formatPrice, planName } from './PlanCard'
import type { Dialog } from '@/shared/lib/use-dialog'

/** 영수증 한 줄: 왼쪽 이름, 오른쪽 값 */
/** line = 이름 · 값 · 굵게 */
function Line({ line }: { line: { label: ReactNode; value: ReactNode; strong?: boolean } }) {
  const { label, value, strong } = line
  return (
    <div className={cn('flex items-baseline justify-between gap-4', strong ? 'text-base font-bold text-gray-900 dark:text-white' : 'text-gray-700 dark:text-gray-300')}>
      <span className="shrink-0">{label}</span>
      <span className="min-w-0 text-right tabular-nums break-all">{value}</span>
    </div>
  )
}

/** 영수증 칸 (위아래 실선으로 나눈다) */
function Block({ children }: { children: ReactNode }) {
  return <div className="flex flex-col gap-1.5 border-t border-gray-300 px-4 py-3 first:border-t-0 dark:border-gray-700">{children}</div>
}

/**
 * 결제 영수증 (POS 영수증처럼): 판매처, 결제 일시·주문번호, 품목, 금액(공급가액·부가세), 결제 수단.
 * 공식 매출전표는 토스 영수증 링크로.
 */
export function ReceiptModal({ dialog }: { /** data = 보여 줄 결제 */ dialog: Dialog<Payment> }) {
  const { open, close: onClose, data: payment } = dialog
  const { t } = useTranslation()
  const fmt = useFormat()
  if (!payment) return null
  const p = payment
  const item = `${planName({ code: p.planCode, name: p.planName })} ${p.billingCycle ? t(`billing.cycle.${p.billingCycle}`) : ''}`.trim()
  const overageAmount = p.overageAmount ?? 0
  const overageMembers = p.overageMembers ?? 0
  const onlyOverage = p.kind === 'OVERAGE'
  // 품목: 플랜 (초과 인원만 결제한 건이면 없음) + 초과 인원
  const rows: [string, number, number][] = [
    ...(onlyOverage ? [] : [[item, 1, p.amount - overageAmount] as [string, number, number]]),
    ...(overageMembers > 0 ? [[t('billing.receipt.overageItem'), overageMembers, overageAmount] as [string, number, number]] : []),
  ]
  const done = p.status === 'DONE'
  const card = [p.cardCompany, p.cardNumberMasked].filter(Boolean).join(' ')

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="sm"
      title={t('billing.receipt.title')}
      footer={
        <>
          {p.receiptUrl && (
            <a
              href={p.receiptUrl}
              target="_blank"
              rel="noreferrer"
              className="mr-auto inline-flex items-center gap-1 self-center text-sm font-semibold text-brand-700 hover:underline dark:text-brand-300"
            >
              {t('billing.receipt.official')}
              <ExternalLink className="size-3.5" />
            </a>
          )}
          <Button variant="outline" onClick={onClose}>
            {t('common.close')}
          </Button>
        </>
      }
    >
      <div className="rounded-lg border border-gray-300 bg-white font-mono text-[13px] leading-relaxed dark:border-gray-700 dark:bg-gray-950">
        <Block>
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="flex items-center gap-1.5 font-sans text-sm font-bold text-gray-900 dark:text-white">
                <Receipt className="size-4 text-brand-600" />
                MQ
              </p>
              <p className="text-gray-500">{t('billing.receipt.seller')}</p>
            </div>
            <span className="text-right text-xs text-gray-500">{SUPPORT_EMAIL}</span>
          </div>
        </Block>

        <Block>
          <Line line={{ label: t('billing.receipt.paidAt'), value: fmt.dateTime(p.approvedAt ?? p.createdAt) }} />
          <Line line={{ label: t('billing.receipt.orderId'), value: <span className="text-xs">{p.orderId}</span> }} />
          {p.teamName && <Line line={{ label: t('billing.receipt.workspace'), value: p.teamName }} />}
        </Block>

        <Block>
          <div className="grid grid-cols-[1fr_auto_auto] gap-x-4 text-xs text-gray-500">
            <span>{t('billing.receipt.item')}</span>
            <span className="text-right">{t('billing.receipt.qty')}</span>
            <span className="text-right">{t('billing.receipt.amount')}</span>
          </div>
          {rows.map(([name, qty, amount]) => (
            <div key={name} className="grid grid-cols-[1fr_auto_auto] gap-x-4 text-gray-900 dark:text-gray-100">
              <span className="min-w-0 break-words">{name}</span>
              <span className="text-right tabular-nums">{qty}</span>
              <span className="text-right tabular-nums">{formatPrice(amount)}</span>
            </div>
          ))}
          {p.periodEnd && <p className="text-xs text-gray-500">{t('billing.receipt.period', { date: fmt.date(p.periodEnd) })}</p>}
        </Block>

        <Block>
          <Line line={{ label: t('billing.receipt.total'), value: formatPrice(p.amount) }} />
          <Line line={{ label: t('billing.receipt.supply'), value: formatPrice(p.supplyAmount) }} />
          <Line line={{ label: t('billing.receipt.vat'), value: formatPrice(p.vat) }} />
        </Block>

        <Block>
          <Line line={{ label: t('billing.receipt.method'), value: p.method ?? '-' }} />
          {card && <Line line={{ label: t('billing.receipt.card'), value: card }} />}
          <Line line={{ label: t('billing.receipt.installment'), value: t('billing.receipt.lumpSum') }} />
          {p.approveNo && <Line line={{ label: t('billing.receipt.approveNo'), value: p.approveNo }} />}
          <Line line={{ label: t('billing.receipt.status'), value: <span className={done ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}>{t(`billing.history.statuses.${p.status}`)}</span> }} />
          {p.failureMessage && <p className="text-xs text-red-600 dark:text-red-400">{p.failureMessage}</p>}
        </Block>

        <Block>
          <Line line={{ label: t('billing.receipt.sum'), value: formatPrice(done ? p.amount : 0), strong: true }} />
        </Block>
      </div>
    </Modal>
  )
}
