import { LoaderCircle } from 'lucide-react'
import { cn } from '@/shared/lib/cn'

export function Spinner({ className }: { className?: string }) {
  return <LoaderCircle aria-hidden className={cn('animate-spin', className ?? 'size-5')} />
}

export function FullPageSpinner() {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-white dark:bg-gray-950">
      <Spinner className="size-8 text-brand-600" />
    </div>
  )
}
