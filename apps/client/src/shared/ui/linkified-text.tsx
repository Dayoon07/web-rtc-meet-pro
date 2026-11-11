import { Fragment } from 'react'
import { cn } from '@/shared/lib/cn'

// http(s):// 주소, www. 주소, 그리고 흔한 도메인(naver.com, example.co.kr 처럼 끝이 알려진 것)만 링크로
const URL_RE =
  /\b((?:https?:\/\/|www\.)[^\s<>"']+|(?:[a-z0-9-]+\.)+(?:com|net|org|kr|io|dev|app|me|ai|jp|cn|co|so|xyz|site|shop|link|info|biz|gg|tv|ly|to)(?:\/[^\s<>"']*)?)/gi
// 문장 끝의 마침표·괄호·따옴표는 주소에 넣지 않는다
const TRAILING = /[.,!?;:)\]}'"…。、]+$/

/** 글 속의 주소를 눌러서 새 탭으로 열 수 있는 파란 링크로 */
export function LinkifiedText({ text, onDark }: { text: string; /** 진한 배경(내 말풍선) 위: 밝은 파랑 */ onDark?: boolean }) {
  const parts: (string | { href: string; label: string })[] = []
  let last = 0
  for (const m of text.matchAll(URL_RE)) {
    const raw = m[0]
    const start = m.index ?? 0
    // 이메일 주소의 도메인 부분(a@b.com)은 링크로 만들지 않는다
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
