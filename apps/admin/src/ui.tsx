import { LoaderCircle, ShieldCheck } from 'lucide-react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

/** 클래스 이어 붙이기 (겹치는 색은 variant 로만 바꾼다) */
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

/** 서비스 웹 로고와 같은 모양 + "관리자" 표시. compact 면 좁은 화면에서 이름을 숨긴다 (헤더용) */
export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <span className="flex shrink-0 items-center gap-2.5">
      <span className="flex size-8 items-center justify-center rounded-lg bg-brand-600 text-white">
        <ShieldCheck className="size-5" />
      </span>
      <span className={cx('font-bold tracking-tight whitespace-nowrap', compact && 'hidden sm:inline')}>Limeet Pro</span>
      <Badge className="bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300">관리자</Badge>
    </span>
  )
}

export const inputClass =
  'h-10 w-full rounded-lg border border-gray-300 bg-white px-3 text-sm text-gray-900 outline-none placeholder:text-gray-400 focus:border-brand-500 focus:ring-3 focus:ring-brand-500/15 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100'
