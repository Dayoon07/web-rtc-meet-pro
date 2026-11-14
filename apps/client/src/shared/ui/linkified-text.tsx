import { Fragment } from 'react'
import { cn } from '@/shared/lib/cn'

const URL_RE =
  /\b((?:https?:\/\/|www\.)[^\s<>"']+|(?:[a-z0-9-]+\.)+(?:com|net|org|kr|io|dev|app|me|ai|jp|cn|co|so|xyz|site|shop|link|info|biz|gg|tv|ly|to)(?:\/[^\s<>"']*)?)/gi
const TRAILING = /[.,!?;:)\]}'"…。、]+$/

export function LinkifiedText({ text, onDark }: { text: string; onDark?: boolean }) {
  const parts: (string | { href: string; label: string })[] = []
  let last = 0
  for (const m of text.matchAll(URL_RE)) {
    const raw = m[0]
    const start = m.index ?? 0
    if (start > 0 && text[start - 1] === '@') continue
    const label = raw.replace(TRAILING, '')
    if (!label.includes('.')) continue
    if (start > last) parts.push(text.slice(last, start))
    parts.push({ href: /^https?:\/\//i.test(label) ? label : `https://${label}`, label })
    last = start + label.length
  }
  if (last < text.length) parts.push(text.slice(last))

  return (
    <>
      {parts.map((p, i) =>
        typeof p === 'string' ? (
          <Fragment key={i}>{p}</Fragment>
        ) : (
          <a
            key={i}
            href={p.href}
            target="_blank"
            rel="noopener noreferrer"
            onClick={(e) => e.stopPropagation()}
            className={cn(
              'break-all underline underline-offset-2',
              onDark ? 'text-sky-200 decoration-sky-200/60 hover:text-white' : 'text-blue-600 decoration-blue-600/40 hover:text-blue-700 dark:text-sky-400 dark:decoration-sky-400/50 dark:hover:text-sky-300',
            )}
          >
            {p.label}
          </a>
        ),
      )}
    </>
  )
}
