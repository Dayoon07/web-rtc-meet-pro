import { Fragment } from 'react'
import { useTranslation } from 'react-i18next'
import type { ChatMessage } from '@/entities/chat'
import { cn } from '@/shared/lib/cn'
import { LinkifiedText } from '@/shared/ui'
import { useRoom } from '../model/context'

const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/**
 * 메시지 글: "@닉네임"·"@모두" 는 강조(나를 부르면 노랗게), 주소는 파란 링크.
 * 강조할 이름은 메시지에 기록된 멘션(mentions·mentionAll)만 쓴다 (그냥 @ 를 쓴 글은 강조하지 않음)
 */
export function MessageText({ message }: { message: ChatMessage }) {
  const { t } = useTranslation()
  const { room, myId } = useRoom()
  const mine = message.sender?.id === myId
  const text = message.content ?? ''
  const people = new Map(room.members.map((m) => [m.id, m.nickname]))
  const names = (message.mentions ?? []).map((id) => people.get(id)).filter((n): n is string => !!n)
  const allLabel = t('chat.mention.all')
  if (message.mentionAll) names.push(allLabel)
  if (!names.length) return <LinkifiedText text={text} onDark={mine} />

  const me = myId ? people.get(myId) : undefined
  const re = new RegExp(`@(${[...new Set(names)].sort((a, b) => b.length - a.length).map(escape).join('|')})`, 'g')
  const parts: { text: string; name?: string }[] = []
  let last = 0
  for (const m of text.matchAll(re)) {
    const at = m.index ?? 0
    if (at > last) parts.push({ text: text.slice(last, at) })
    parts.push({ text: m[0], name: m[1] })
    last = at + m[0].length
  }
  if (last < text.length) parts.push({ text: text.slice(last) })

  return (
    <>
      {parts.map((p, i) =>
        p.name ? (
          <span
            key={i}
            className={cn(
              'rounded px-0.5 font-semibold',
              p.name === me || p.name === allLabel
                ? 'bg-amber-200/80 text-amber-900 dark:bg-amber-400/30 dark:text-amber-100'
                : mine
                  ? 'bg-white/20 text-white'
                  : 'bg-brand-100 text-brand-800 dark:bg-brand-500/25 dark:text-brand-100',
            )}
          >
            {p.text}
          </span>
        ) : (
          <Fragment key={i}>
            <LinkifiedText text={p.text} onDark={mine} />
          </Fragment>
        ),
      )}
    </>
  )
}
