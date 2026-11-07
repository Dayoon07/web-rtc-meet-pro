import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn('rounded-2xl border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-gray-900', className)}>
      {children}
    </div>
  )
}

export function Badge({ children, tone = 'brand', className }: { children: ReactNode; tone?: 'brand' | 'gray' | 'green'; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold whitespace-nowrap',
        tone === 'brand' && 'bg-brand-100 text-brand-700 dark:bg-brand-500/20 dark:text-brand-200',
        tone === 'gray' && 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300',
        tone === 'green' && 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300',
        className,
      )}
    >
      {children}
    </span>
  )
}

export function Progress({ value, max }: { value: number; max: number }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0
  return (
    <div className="h-2 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800" role="progressbar" aria-valuenow={value} aria-valuemax={max}>
      <div
        className={cn('h-full rounded-full transition-all', pct >= 100 ? 'bg-amber-500' : 'bg-brand-600 dark:bg-brand-500')}
        style={{ width: `${pct}%` }}
      />
    </div>
  )
}

export function EmptyState({ icon, title, description, action }: { icon: ReactNode; title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-gray-300 px-6 py-10 text-center dark:border-gray-700">
      <span className="flex size-12 items-center justify-center rounded-full bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400 [&_svg]:size-6">
        {icon}
      </span>
      <div>
        <p className="font-semibold text-gray-900 dark:text-white">{title}</p>
        {description && <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">{description}</p>}
      </div>
      {action}
    </div>
  )
}

export function Divider({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
      <span className="h-px flex-1 bg-gray-200 dark:bg-gray-800" />
      {label}
      <span className="h-px flex-1 bg-gray-200 dark:bg-gray-800" />
    </div>
  )
}
