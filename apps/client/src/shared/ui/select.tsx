import { Check, ChevronDown } from 'lucide-react'
import {
  type ChangeEvent,
  Children,
  type CSSProperties,
  Fragment,
  isValidElement,
  type KeyboardEvent,
  type ReactNode,
  type SelectHTMLAttributes,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
} from 'react'
import { createPortal } from 'react-dom'
import { cn } from '@/shared/lib/cn'

type Option = { value: string; label: ReactNode; text: string; disabled: boolean }

const textOf = (node: ReactNode): string => {
  if (node == null || typeof node === 'boolean') return ''
  if (typeof node === 'string' || typeof node === 'number') return String(node)
  if (Array.isArray(node)) return node.map(textOf).join('')
  if (isValidElement(node)) return textOf((node.props as { children?: ReactNode }).children)
  return ''
}

/** <option> 자식(배열·Fragment·optgroup 안도)을 선택지 목록으로 */
function collect(children: ReactNode, out: Option[] = []): Option[] {
  Children.forEach(children, (child) => {
    if (!isValidElement(child)) return
    const props = child.props as { value?: string | number; children?: ReactNode; disabled?: boolean }
    if (child.type === Fragment || child.type === 'optgroup') collect(props.children, out)
    else if (child.type === 'option') {
      const text = textOf(props.children)
      out.push({ value: String(props.value ?? text), label: props.children, text, disabled: !!props.disabled })
    }
  })
  return out
}

type Props = SelectHTMLAttributes<HTMLSelectElement> & {
  invalid?: boolean
  compact?: boolean
  /** 버튼 모양 (field.tsx 의 입력칸 클래스) */
  controlClass: string
}

/**
 * 브라우저 기본 드롭다운 대신 화면 테마에 맞춘 선택 상자.
 * 쓰는 쪽은 <select> 와 똑같이: value · onChange(e.target.value) · <option> 자식.
 * 목록은 body 에 띄워서 모달·스크롤 영역에 잘리지 않는다. 키보드: ↑↓ Home End Enter Esc, 글자 입력으로 찾기
 */
export function CustomSelect({ className, controlClass, invalid, compact, children, value, onChange, disabled, id, name, ...rest }: Props) {
  const all = collect(children)
  const current = String(value ?? '')
  const selected = all.find((o) => o.value === current)
  // "선택하세요" 같은 비활성 빈 값은 버튼에만 보이고 목록에는 넣지 않는다
  const options = all.filter((o) => !(o.disabled && o.value === ''))

  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [style, setStyle] = useState<CSSProperties>()
  const button = useRef<HTMLButtonElement>(null)
  const list = useRef<HTMLUListElement>(null)
  const typed = useRef({ text: '', at: 0 })
  const listId = useId()

  const place = () => {
    const r = button.current?.getBoundingClientRect()
    if (!r) return
    const below = window.innerHeight - r.bottom - 8
    const above = r.top - 8
    const up = below < 200 && above > below
    setStyle({
      position: 'fixed',
      left: Math.min(r.left, window.innerWidth - Math.max(r.width, 160) - 8),
      minWidth: r.width,
      maxWidth: Math.max(r.width, 320),
      ...(up ? { bottom: window.innerHeight - r.top + 4, maxHeight: Math.min(288, above) } : { top: r.bottom + 4, maxHeight: Math.min(288, below) }),
    })
  }

  const openList = (at?: number) => {
    if (disabled) return
    place()
    const idx = options.findIndex((o) => o.value === current)
    setActive(at ?? (idx >= 0 ? idx : options.findIndex((o) => !o.disabled)))
    setOpen(true)
  }

  const choose = (i: number) => {
    const o = options[i]
    if (!o || o.disabled) return
    setOpen(false)
    button.current?.focus()
    if (o.value !== current) {
      const target = { value: o.value, name: name ?? '' }
      onChange?.({ target, currentTarget: target } as unknown as ChangeEvent<HTMLSelectElement>)
    }
  }

  const step = (from: number, dir: 1 | -1) => {
    for (let i = from + dir; i >= 0 && i < options.length; i += dir) if (!options[i].disabled) return i
    return from
  }

  useEffect(() => {
    if (!open) return
    const onPointer = (e: PointerEvent) => {
      const t = e.target as Node
      if (!button.current?.contains(t) && !list.current?.contains(t)) setOpen(false)
    }
    const onScroll = (e: Event) => {
      if (!list.current?.contains(e.target as Node)) place()
    }
    document.addEventListener('pointerdown', onPointer)
    window.addEventListener('scroll', onScroll, true)
    window.addEventListener('resize', place)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      window.removeEventListener('scroll', onScroll, true)
      window.removeEventListener('resize', place)
    }
  }, [open])

  useLayoutEffect(() => {
    if (open && active >= 0) list.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [open, active])

  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    const key = e.key
    if (key === 'Tab') return setOpen(false)
    if (key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey && key !== ' ') {
      // 글자를 치면 그 글자로 시작하는 선택지로
      const now = Date.now()
      typed.current = { text: (now - typed.current.at < 700 ? typed.current.text : '') + key.toLowerCase(), at: now }
      const hit = options.findIndex((o) => !o.disabled && o.text.toLowerCase().startsWith(typed.current.text))
      if (hit >= 0) (open ? setActive(hit) : openList(hit))
      return
    }
    if (!open) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(key)) {
        e.preventDefault()
        openList()
      }
      return
    }
    if (key === 'Escape') {
      e.preventDefault()
      e.stopPropagation() // 모달 안에서도 목록만 닫히게
      setOpen(false)
    } else if (key === 'ArrowDown' || key === 'ArrowUp') {
      e.preventDefault()
      setActive((a) => step(a, key === 'ArrowDown' ? 1 : -1))
    } else if (key === 'Home' || key === 'End') {
      e.preventDefault()
      setActive(key === 'Home' ? step(-1, 1) : step(options.length, -1))
    } else if (key === 'Enter' || key === ' ') {
      e.preventDefault()
      choose(active)
    }
  }

  return (
    <>
      <button
        ref={button}
        id={id}
        type="button"
        disabled={disabled}
        aria-label={rest['aria-label']}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-invalid={invalid || undefined}
        onClick={() => (open ? setOpen(false) : openList())}
        onKeyDown={onKeyDown}
        className={cn(controlClass, 'relative inline-flex items-center pr-8 text-left', className)}
      >
        <span className={cn('block min-w-0 flex-1 truncate', (!selected || selected.value === '') && selected?.disabled && 'text-gray-400')}>
          {selected?.label}
        </span>
        <ChevronDown
          className={cn(
            'pointer-events-none absolute top-1/2 right-2.5 -translate-y-1/2 text-gray-400 transition-transform',
            compact ? 'size-3.5' : 'size-4',
            open && 'rotate-180',
          )}
        />
      </button>
      {name && <input type="hidden" name={name} value={current} />}
      {open &&
        style &&
        createPortal(
          <ul
            ref={list}
            id={listId}
            role="listbox"
            aria-label={rest['aria-label']}
            style={style}
            onMouseDown={(e) => e.preventDefault()}
            className="z-[200] overflow-y-auto rounded-xl border border-gray-200 bg-white p-1 shadow-lg shadow-gray-900/10 dark:border-gray-700 dark:bg-gray-900 dark:shadow-black/50"
          >
            {options.map((o, i) => {
              const isSelected = o.value === current
              return (
                <li
                  key={o.value}
                  role="option"
                  data-index={i}
                  aria-selected={isSelected}
                  aria-disabled={o.disabled || undefined}
                  onMouseEnter={() => !o.disabled && setActive(i)}
                  onClick={() => choose(i)}
                  className={cn(
                    'flex items-center gap-2 rounded-lg px-3 text-gray-800 dark:text-gray-100',
                    compact ? 'py-1.5 text-xs' : 'py-2 text-sm',
                    i === active && 'bg-gray-100 dark:bg-gray-800',
                    isSelected && 'font-semibold text-brand-700 dark:text-brand-300',
                    o.disabled && 'cursor-default opacity-40',
                  )}
                >
                  <span className="min-w-0 flex-1 truncate">{o.label}</span>
                  {isSelected && <Check className="size-4 shrink-0" />}
                </li>
              )
            })}
          </ul>,
          document.body,
        )}
    </>
  )
}
