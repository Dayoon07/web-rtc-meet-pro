import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'

const sizes = {
  xs: 'size-6 text-[10px]',
  sm: 'size-8 text-xs',
  md: 'size-10 text-sm',
  lg: 'size-14 text-lg',
  xl: 'size-24 text-3xl',
} as const

const palette = [
  'bg-violet-100 text-violet-700 dark:bg-violet-500/20 dark:text-violet-200',
  'bg-sky-100 text-sky-700 dark:bg-sky-500/20 dark:text-sky-200',
  'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-200',
  'bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-200',
  'bg-rose-100 text-rose-700 dark:bg-rose-500/20 dark:text-rose-200',
  'bg-indigo-100 text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-200',
]

const initials = (name: string) => {
  const t = name.trim()
  if (!t) return '?'
  if (/[가-힣]/.test(t[0])) return t[0]
  const words = t.split(/\s+/).filter((w) => /^[\p{L}\p{N}]/u.test(w))
  return (words.length ? words : [t])
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join('')
}

const colorOf = (name: string) => palette[[...name].reduce((sum, c) => sum + c.charCodeAt(0), 0) % palette.length]

type Props = {
  src?: string | null
  name: string
  size?: keyof typeof sizes
  className?: string
  ring?: boolean
}

export function Avatar({ src, name, size = 'md', className, ring }: Props) {
  const { t } = useTranslation()
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const showImage = src && failedSrc !== src

  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full font-semibold select-none',
        sizes[size],
        ring && 'ring-2 ring-white dark:ring-gray-950',
        !showImage && colorOf(name),
        className,
      )}
    >
      {showImage ? (
        <img
          src={src}
          alt={t('profile.photo.alt', { name })}
          referrerPolicy="no-referrer"
          className="size-full object-cover"
          onError={() => setFailedSrc(src)}
        />
      ) : (
        <span aria-label={name}>{initials(name)}</span>
      )}
    </span>
  )
}
