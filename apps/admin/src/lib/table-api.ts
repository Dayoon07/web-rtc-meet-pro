import { downloadFile, http } from './api'

export type ColumnKind = 'TEXT' | 'LONG_TEXT' | 'NUMBER' | 'DATETIME' | 'YN' | 'ENUM' | 'BINARY'

export type ColumnRef = { table: string; column: string }

export type TableColumn = {
  name: string
  comment: string | null
  kind: ColumnKind
  dbType: string
  maxLength: number | null
  nullable: boolean
  primaryKey: boolean
  editable: boolean
  secret: boolean
  options: string[]
  references: ColumnRef | null
  referencedBy: ColumnRef[]
}

export type TableSummary = {
  name: string
  label: string
  group: string
  description: string | null
  rowCount: number
  readOnly: boolean
  insertable: boolean
}

export type TableMeta = Omit<TableSummary, 'rowCount'> & {
  primaryKey: string | null
  softDelete: boolean
  columns: TableColumn[]
}

export type Row = Record<string, string | number | boolean | null>

export type RowPage = { rows: Row[]; total: number; page: number; size: number }

export type RowQuery = {
  q?: string
  sort?: string
  desc?: boolean
  page?: number
  size?: number
  deleted?: 'active' | 'deleted'
  filters?: [string, string][]
  contains?: [string, string][]
}

const qs = (query: RowQuery, withPaging = true) => {
  const p = new URLSearchParams()
  if (query.q) p.set('q', query.q)
  if (query.sort) p.set('sort', query.sort)
  if (query.desc === false) p.set('desc', 'false')
  if (withPaging) {
    p.set('page', String(query.page ?? 0))
    p.set('size', String(query.size ?? 50))
  }
  if (query.deleted) p.set('deleted', query.deleted)
  query.filters?.forEach(([c, v]) => p.append('filter', `${c}:${v}`))
  query.contains?.forEach(([c, v]) => p.append('contains', `${c}:${v}`))
  return p.toString()
}

const base = (table: string) => `/admin/tables/${encodeURIComponent(table)}`

export const tableApi = {
  list: () => http<TableSummary[]>('/admin/tables'),
  meta: (table: string) => http<TableMeta>(base(table)),
  rows: (table: string, query: RowQuery) => http<RowPage>(`${base(table)}/rows?${qs(query)}`),
  row: (table: string, id: string) => http<Row>(`${base(table)}/rows/${encodeURIComponent(id)}`),
  update: (table: string, id: string, values: Record<string, unknown>) =>
    http<Row>(`${base(table)}/rows/${encodeURIComponent(id)}`, { method: 'PATCH', json: { values } }),
  insert: (table: string, values: Record<string, unknown>) => http<Row>(`${base(table)}/rows`, { method: 'POST', json: { values } }),
  exportCsv: (table: string, query: RowQuery) => downloadFile(`${base(table)}/export?${qs(query, false)}`, `${table}.csv`),
}

export const tableKeys = {
  list: ['admin', 'tables'] as const,
  meta: (t: string) => ['admin', 'tables', t, 'meta'] as const,
  rows: (t: string) => ['admin', 'tables', t, 'rows'] as const,
  row: (t: string, id: string) => ['admin', 'tables', t, 'row', id] as const,
}

export function toLocalInput(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function fromLocalInput(value: string): string {
  if (!value) return ''
  const withSeconds = value.length === 16 ? `${value}:00` : value
  const offset = -new Date(withSeconds).getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const abs = Math.abs(offset)
  return `${withSeconds}${sign}${String(Math.floor(abs / 60)).padStart(2, '0')}:${String(abs % 60).padStart(2, '0')}`
}
