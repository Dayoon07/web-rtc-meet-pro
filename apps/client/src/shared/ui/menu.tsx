import { type ReactNode, useEffect, useId, useRef, useState } from 'react'
import { cn } from '@/shared/lib/cn'

export function Menu({
  trigger,
  children,
  align = 'end',
  label,
  panelClassName,
}: {
  trigger: (props: { open: boolean; toggle: () => void; id: string }) => ReactNode
  children: (close: () => void) => ReactNode
  align?: 'start' | 'end'
  label: string
  panelClassName?: string
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const id = useId()

  useEffect(() => {
    if (!open) return
    const onPointer = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div ref={ref} className="relative">
      {trigger({ open, toggle: () => setOpen((v) => !v), id })}
      {open && (
        <div
          id={id}
          role="menu"
          aria-label={label}
          className={cn(
            'absolute z-50 mt-2 origin-top rounded-xl border border-gray-200 bg-white shadow-lg shadow-gray-900/5 dark:border-gray-800 dark:bg-gray-900 dark:shadow-black/40',
            panelClassName ?? 'w-64 p-1.5',
            align === 'end' ? 'right-0' : 'left-0',
          )}
        >
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  )
}

export function MenuItem({
  icon,
  children,
  onClick,
  danger,
}: {
  icon?: ReactNode
  children: ReactNode
  onClick?: () => void
  danger?: boolean
}) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm transition-colors [&_svg]:size-4',
        danger
          ? 'text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10'
          : 'text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-800',
      )}
    >
      {icon}
      {children}
    </button>
  )
}

export const MenuSeparator = () => <div className="my-1.5 h-px bg-gray-200 dark:bg-gray-800" />
