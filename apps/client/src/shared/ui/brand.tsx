import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { cn } from '@/shared/lib/cn'
import { ROUTES } from '@/shared/config/routes'

export function MqMark({ className, mono }: { className?: string; mono?: boolean }) {
  return (
    <svg viewBox="0 0 100 92" aria-hidden className={cn('size-8', mono ? 'text-current' : 'text-[#7C1FFF] dark:text-brand-400', className)}>
      <g fill="currentColor">
        <path d="M4 24C4 11 14 1 28 1H66A12 12 0 0 1 75 21L54 41 38 30 19 48 4 56 6 43 4 42Z" />
        <path d="M26 52 38 43 54 53 80 30C86 25 98 28 98 40V56C98 64 93 70 86 74L87 90 68 77H35C18 77 14 64 20 57Z" />
      </g>
    </svg>
  )
}

export const BRAND_NAME = 'MQ'

export function Logo({ to = ROUTES.home, className, compact }: { to?: string; className?: string; compact?: boolean }) {
  const { t } = useTranslation()
  return (
    <Link to={to} className={cn('inline-flex items-center gap-2', className)} aria-label={t('nav.logoHome')}>
      <MqMark className="h-7 w-8" />
      {!compact && <span className="text-xl font-black tracking-tight text-gray-950 dark:text-white">{BRAND_NAME}</span>}
    </Link>
  )
}

export function GoogleMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 48 48" aria-hidden className={cn('size-5', className)}>
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34.1 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.6-.4-3.5z" />
      <path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34.1 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.6-.4-3.5z" />
    </svg>
  )
}
