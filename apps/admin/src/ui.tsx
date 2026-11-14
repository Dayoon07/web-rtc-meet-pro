import { LoaderCircle } from 'lucide-react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

export const cx = (...parts: (string | false | null | undefined)[]) => parts.filter(Boolean).join(' ')

export function Spinner({ className = 'size-5' }: { className?: string }) {
  return <LoaderCircle className={cx('animate-spin text-gray-400', className)} aria-hidden />
}

export function FullPageSpinner() {
  return (
    <div className="flex min-h-dvh items-center justify-center">
      <Spinner className="size-7" />
    </div>
  )
}

type Variant = 'primary' | 'outline' | 'ghost'

const variants: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white shadow-sm hover:bg-brand-700 dark:bg-brand-500 dark:hover:bg-brand-400',
  outline:
    'border border-gray-300 bg-white text-gray-800 hover:bg-gray-50 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100 dark:hover:bg-gray-800',
  ghost: 'text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800',
}

export function Button({
  variant = 'primary',
  loading,
  icon,
  className,
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean; icon?: ReactNode }) {
  return (
    <button
      className={cx(
        'inline-flex h-9 items-center justify-center gap-2 rounded-lg px-3.5 text-sm font-semibold whitespace-nowrap transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 disabled:pointer-events-none disabled:opacity-50',
        variants[variant],
        className,
      )}
      disabled={disabled || loading}
      {...props}
    >
      {loading ? <Spinner className="size-4" /> : icon}
      {children}
    </button>
  )
}

export function Badge({ className, children }: { className: string; children: ReactNode }) {
  return <span className={cx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold whitespace-nowrap', className)}>{children}</span>
}

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <section className={cx('rounded-2xl border border-gray-200 bg-white shadow-xs dark:border-gray-800 dark:bg-gray-900', className)}>
      {children}
    </section>
  )
}

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <span className="flex shrink-0 items-center gap-2.5">
      <svg viewBox="0 0 100 92" aria-hidden className="h-7 w-8 text-[#7C1FFF] dark:text-brand-400">
        <g fill="currentColor">
          <path d="M4 24C4 11 14 1 28 1H66A12 12 0 0 1 75 21L54 41 38 30 19 48 4 56 6 43 4 42Z" />
          <path d="M26 52 38 43 54 53 80 30C86 25 98 28 98 40V56C98 64 93 70 86 74L87 90 68 77H35C18 77 14 64 20 57Z" />
        </g>
      </svg>
      <span className={cx('text-lg font-black tracking-tight whitespace-nowrap', compact && 'hidden sm:inline')}>MQ</span>
      <Badge className="bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300">관리자</Badge>
    </span>
  )
}

export const inputClass =
  'h-10 w-full rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-900 outline-none placeholder:text-gray-400 focus:border-brand-500 focus:ring-3 focus:ring-brand-500/15 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100'
