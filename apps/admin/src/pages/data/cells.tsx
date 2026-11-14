import { Link } from 'react-router'
import { formatDateTime } from '@/lib/labels'
import type { Row, TableColumn } from '@/lib/table-api'
import { Badge, cx } from '@/ui'

const number = new Intl.NumberFormat('ko-KR')

function toneOf(value: string) {
  if (/^(ACTIVE|ACCEPTED|ONGOING|LIVE|PAID|DONE|SUCCESS|ADMIN|OWNER)$/.test(value)) return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-500/15 dark:text-emerald-300'
  if (/^(PENDING|SCHEDULED|READY|WAITING)$/.test(value)) return 'bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-300'
  if (/^(DELETED|CANCELED|CANCELLED|FAILED|EXPIRED|DECLINED|SUSPENDED|REVOKED|ENDED)$/.test(value)) return 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300'
  return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300'
}

export function refLink(col: TableColumn, value: Row[string]) {
  if (!col.references || value === null || value === '') return null
  return `/data/${col.references.table}?row=${encodeURIComponent(String(value))}`
}

export function Cell({ col, value }: { col: TableColumn; value: Row[string] }) {
  if (col.secret) return value ? <span className="text-gray-400">●●●● 있음</span> : <span className="text-gray-300 dark:text-gray-600">-</span>
  if (value === null || value === '') return <span className="text-gray-300 dark:text-gray-600">-</span>
  const link = refLink(col, value)
  if (link) {
    return (
      <Link to={link} onClick={(e) => e.stopPropagation()} className="font-mono text-brand-700 hover:underline dark:text-brand-300" title={`${col.references!.table} 에서 보기`}>
        {String(value)}
      </Link>
    )
  }
  switch (col.kind) {
    case 'YN':
      return <Badge className={value === 'Y' ? 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300' : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400'}>{String(value)}</Badge>
    case 'ENUM':
      return <Badge className={toneOf(String(value))}>{String(value)}</Badge>
    case 'NUMBER':
      return <span className={cx('tabular-nums', col.primaryKey && 'font-mono font-semibold')}>{col.primaryKey || col.name.endsWith('_id') || col.name.endsWith('_us') ? String(value) : number.format(Number(value))}</span>
    case 'DATETIME':
      return <span className="tabular-nums whitespace-nowrap">{formatDateTime(String(value))}</span>
    case 'BINARY':
      return <span className="text-gray-400">[{number.format(Number(value))} bytes]</span>
    default:
      return (
        <span className="block max-w-72 truncate" title={String(value)}>
          {String(value)}
        </span>
      )
  }
}
