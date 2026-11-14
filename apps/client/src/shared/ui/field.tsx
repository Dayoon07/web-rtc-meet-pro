import { CircleAlert } from 'lucide-react'
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'
import { cn } from '@/shared/lib/cn'
import { CustomSelect } from './select'

const controlBase =
  'rounded-lg border bg-white text-gray-900 shadow-xs outline-none transition placeholder:text-gray-400 focus:border-brand-500 focus:ring-3 focus:ring-brand-500/15 disabled:bg-gray-50 disabled:text-gray-500 dark:bg-gray-900 dark:text-gray-100 dark:placeholder:text-gray-500 dark:disabled:bg-gray-800'
const control = `h-11 w-full px-3.5 text-[15px] ${controlBase}`
const controlCompact = `h-8 w-auto pl-2.5 text-xs ${controlBase}`

const controlBorder = (invalid?: boolean) =>
  invalid ? 'border-red-400 dark:border-red-500' : 'border-gray-300 dark:border-gray-700'

type FieldProps = {
  label: string
  htmlFor?: string
  required?: boolean
  hint?: ReactNode
  error?: string | null
  children: ReactNode
  className?: string
}

export function Field({ label, htmlFor, required, hint, error, children, className }: FieldProps) {
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={htmlFor} className="text-sm font-medium text-gray-700 dark:text-gray-300">
        {label}
        {required && <span className="ml-0.5 text-brand-600 dark:text-brand-400">*</span>}
      </label>
      {children}
      {error ? <FieldError>{error}</FieldError> : hint && <p className="text-xs text-gray-500 dark:text-gray-400">{hint}</p>}
    </div>
  )
}

export function FieldError({ children }: { children?: ReactNode }) {
  if (!children) return null
  return (
    <p role="alert" className="flex items-center gap-1 text-xs text-red-600 dark:text-red-400">
      <CircleAlert className="size-3.5 shrink-0" />
      {children}
    </p>
  )
}

export function Input({ className, invalid, ...props }: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return <input aria-invalid={invalid || undefined} className={cn(control, controlBorder(invalid), className)} {...props} />
}

export function Select({ invalid, compact, ...props }: SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean; compact?: boolean }) {
  return (
    <CustomSelect
      invalid={invalid}
      compact={compact}
      controlClass={cn(compact ? controlCompact : control, controlBorder(invalid))}
      {...props}
    />
  )
}

export function Checkbox({
  label,
  description,
  className,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: ReactNode; description?: ReactNode }) {
  return (
    <label className={cn('flex cursor-pointer items-start gap-3', className)}>
      <input
        type="checkbox"
        className="mt-0.5 size-4.5 shrink-0 cursor-pointer rounded accent-brand-600"
        {...props}
      />
      <span className="flex flex-col">
        <span className="text-sm text-gray-800 dark:text-gray-200">{label}</span>
        {description && <span className="text-xs text-gray-500 dark:text-gray-400">{description}</span>}
      </span>
    </label>
  )
}

export function Segmented<T extends string>({
  value,
  options,
  onChange,
  name,
}: {
  value: T | null
  options: { value: T; label: ReactNode }[]
  onChange: (value: T | null) => void
  name: string
}) {
  return (
    <div role="radiogroup" aria-label={name} className="grid auto-cols-fr grid-flow-col gap-1 rounded-lg bg-gray-100 p-1 dark:bg-gray-800">
      {options.map((o) => {
        const active = o.value === value
        return (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(active ? null : o.value)}
            className={cn(
              'flex h-9 items-center justify-center gap-1.5 rounded-md text-sm font-medium transition',
              active
                ? 'bg-white text-brand-700 shadow-sm dark:bg-gray-950 dark:text-brand-300'
                : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white',
            )}
          >
            {o.label}
          </button>
        )
      })}
    </div>
  )
}
